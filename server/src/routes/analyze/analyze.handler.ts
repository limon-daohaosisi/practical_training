import { randomUUID } from "node:crypto";

import type { FastifyReply, FastifyRequest } from "fastify";

import type { AgentRunner } from "../../agents/agent-runner.js";
import type { DbClient } from "../../db/client.js";
import { isDeviceBusy } from "../../db/queries/conversations.js";
import {
  ConversationNotFoundError,
  InvalidConversationStateError,
  continueConversationFromObservedEvent,
  startConversationFromSpeech,
} from "../../db/transactions/conversations.js";
import { saveUserMessage, saveAssistantGuidance } from "../../db/transactions/messages.js";
import {
  completeRun,
  createRun,
  failRun,
  getLatestRunId,
  getNextRunIndex,
} from "../../db/transactions/runs.js";
import { saveScreenSnapshot } from "../../db/transactions/snapshots.js";
import { persistAnalyzeDebugPayload } from "../../services/analyze-debug-storage.js";
import {
  assembleAgentRunInput,
  resolveGoalForObserved,
  resolveGoalForSpeech,
} from "../../services/assemble-agent-input.js";
import type {
  AnalyzeError,
  AnalyzeMetadata,
  AnalyzeResponse,
} from "./analyze.schema.js";
import { analyzeMetadataSchema } from "./analyze.schema.js";

export function createAnalyzeHandler(
  agentRunner: AgentRunner,
  dbClient?: DbClient,
) {
  return async function analyzeHandler(
    request: FastifyRequest,
    reply: FastifyReply,
  ) {
    const contentType = request.headers["content-type"] ?? "";
    const body = Buffer.isBuffer(request.body) ? request.body : null;

    if (!contentType.includes("multipart/form-data") || body === null) {
      return reply.status(400).send({
        code: "INVALID_REQUEST",
        message: "metadata and screenshot are required.",
      } satisfies AnalyzeError);
    }

    /* ------------------------------------------------------------------ */
    /*  Parse & validate multipart payload                                  */
    /* ------------------------------------------------------------------ */

    let metadata: AnalyzeMetadata;
    let screenshotBuffer: Buffer;
    let screenshotMimeType: string;
    let savedMetadataPath: string;
    let savedScreenshotPath: string;

    try {
      const persisted = await persistAnalyzeDebugPayload({ contentType, body });
      metadata = analyzeMetadataSchema.parse(
        JSON.parse(persisted.metadataJson),
      ) as AnalyzeMetadata;
      screenshotBuffer = persisted.screenshotBytes;
      screenshotMimeType = persisted.screenshotMimeType;
      savedMetadataPath = persisted.savedMetadataPath;
      savedScreenshotPath = persisted.savedScreenshotPath;
    } catch {
      return reply.status(400).send({
        code: "INVALID_REQUEST",
        message: "metadata and screenshot are required.",
      } satisfies AnalyzeError);
    }

    /* ------------------------------------------------------------------ */
    /*  Without DB — run agent directly (existing contract test path)      */
    /* ------------------------------------------------------------------ */

    if (!dbClient) {
      const conversationId = metadata.conversationId ?? randomUUID();
      const runId = randomUUID();

      const runnerOutput = await agentRunner.run({
        conversationId,
        runId,
        goal: metadata.messageText ?? "",
        messageType: metadata.messageType,
        messageText: metadata.messageText,
        currentSnapshot: {
          packageName: metadata.packageName,
          activityName: metadata.activityName ?? null,
          screenWidth: metadata.screenWidth,
          screenHeight: metadata.screenHeight,
          imageWidth: metadata.imageWidth,
          imageHeight: metadata.imageHeight,
          nodes: metadata.nodes,
        },
        recentMessages: [],
        screenshot: { buffer: screenshotBuffer, mimeType: screenshotMimeType },
      });

      return reply.send({
        conversationId,
        runId,
        conversationStatus: runnerOutput.shouldContinue
          ? "waiting_interaction"
          : "completed",
        answer: runnerOutput.answer,
        target: runnerOutput.target,
        action: runnerOutput.action,
        savedMetadataPath,
        savedScreenshotPath,
      } satisfies AnalyzeResponse);
    }

    /* ------------------------------------------------------------------ */
    /*  With DB — full control-plane pipeline                               */
    /* ------------------------------------------------------------------ */

    const { db } = dbClient;

    /* ---- A.2: device-level concurrency check ---- */
    const busy = await isDeviceBusy(db, metadata.deviceId);
    if (busy) {
      return reply.status(409).send({
        code: "DEVICE_BUSY",
        message: `Device ${metadata.deviceId} already has an active analysis in progress.`,
      } satisfies AnalyzeError);
    }

    /* ---- A.1: route by message type ---- */
    const isSpeech = metadata.messageType === "speech_text";
    let conversationId: string;
    let goal: string;

    try {
      if (isSpeech) {
        const conv = await startConversationFromSpeech(
          db,
          metadata.deviceId,
          metadata.packageName,
        );
        conversationId = conv.id;
        goal = resolveGoalForSpeech(metadata);
      } else {
        if (!metadata.conversationId) {
          return reply.status(400).send({
            code: "INVALID_REQUEST",
            message: "conversationId is required for observed events.",
          } satisfies AnalyzeError);
        }

        const conv = await continueConversationFromObservedEvent(
          db,
          metadata.deviceId,
          metadata.conversationId,
        );
        conversationId = conv.id;
        goal = await resolveGoalForObserved(db, conversationId);
      }
    } catch (error) {
      if (error instanceof ConversationNotFoundError) {
        return reply.status(404).send({
          code: "INVALID_REQUEST",
          message: error.message,
        } satisfies AnalyzeError);
      }
      if (error instanceof InvalidConversationStateError) {
        return reply.status(409).send({
          code: "INVALID_STATE",
          message: error.message,
        } satisfies AnalyzeError);
      }
      throw error;
    }

    /* ---- A.1: create run + save snapshot + save user message ---- */
    const runId = randomUUID();
    const runIndex = await getNextRunIndex(db, conversationId);
    const previousRunId = await getLatestRunId(db, conversationId);

    await createRun(db, {
      conversationId,
      runIndex,
      previousRunId,
      modelProvider: "mock",
      modelName: "mock",
      inputContextJson: {
        goal,
        messageType: metadata.messageType,
        messageText: metadata.messageText,
        packageName: metadata.packageName,
        activityName: metadata.activityName,
        screenWidth: metadata.screenWidth,
        screenHeight: metadata.screenHeight,
      },
    });

    await saveScreenSnapshot(db, {
      conversationId,
      runId,
      packageName: metadata.packageName,
      activityName: metadata.activityName ?? null,
      screenWidth: metadata.screenWidth,
      screenHeight: metadata.screenHeight,
      imageWidth: metadata.imageWidth,
      imageHeight: metadata.imageHeight,
      nodes: metadata.nodes,
      capturedAt: new Date(),
    });

    await saveUserMessage(db, {
      conversationId,
      runId,
      messageType: metadata.messageType,
      text: metadata.messageText,
    });

    /* ---- A.3: assemble + run agent + A.4: write back ---- */
    try {
      const agentInput = await assembleAgentRunInput({
        metadata,
        conversationId,
        runId,
        goal,
        db,
        screenshotBuffer,
        screenshotMimeType,
      });

      const startedAt = Date.now();
      const runnerOutput = await agentRunner.run(agentInput);
      const latencyMs = Date.now() - startedAt;

      await completeRun(db, runId, conversationId, runnerOutput, latencyMs);

      await saveAssistantGuidance(db, {
        conversationId,
        runId,
        text: runnerOutput.answer,
      });

      return reply.send({
        conversationId,
        runId,
        conversationStatus: runnerOutput.shouldContinue
          ? "waiting_interaction"
          : "completed",
        answer: runnerOutput.answer,
        target: runnerOutput.target,
        action: runnerOutput.action,
        savedMetadataPath,
        savedScreenshotPath,
      } satisfies AnalyzeResponse);
    } catch (error) {
      await failRun(
        db,
        runId,
        conversationId,
        "ANALYSIS_FAILED",
        error instanceof Error ? error.message : "Agent run failed.",
      );

      return reply.status(400).send({
        code: "ANALYSIS_FAILED",
        message:
          error instanceof Error ? error.message : "Agent run failed.",
      } satisfies AnalyzeError);
    }
  };
}
