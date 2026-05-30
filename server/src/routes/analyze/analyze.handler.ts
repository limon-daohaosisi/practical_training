import { randomUUID } from "node:crypto";

import type { FastifyReply, FastifyRequest } from "fastify";

import type { AgentRunner } from "../../agents/agent-runner.js";
import type { DbClient } from "../../db/client.js";
import {
  DeviceBusyError,
  ConversationNotFoundError,
  InvalidConversationStateError,
  continueConversationFromObservedEvent,
  startConversationFromSpeech,
} from "../../db/transactions/conversations.js";
import {
  saveUserMessage,
  saveAssistantGuidance,
} from "../../db/transactions/messages.js";
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

    if (process.env.ANALYZE_MOCK_ACTION === "tap") {
      const fallbackWidth = Math.max(1, metadata.screenWidth);
      const fallbackHeight = Math.max(1, metadata.screenHeight);
      const fallbackTarget = metadata.nodes.find((node) => node.clickable);
      const conversationId = metadata.conversationId ?? randomUUID();
      const runId = randomUUID();

      return reply.send({
        conversationId,
        runId,
        conversationStatus: "waiting_interaction",
        answer: "临时 mock：请点击高亮区域",
        target: fallbackTarget
          ? {
              label:
                fallbackTarget.text ||
                fallbackTarget.contentDescription ||
                "目标区域",
              bounds: fallbackTarget.bounds,
            }
          : {
              label: "目标区域",
              bounds: {
                left: Math.round(fallbackWidth * 0.2),
                top: Math.round(fallbackHeight * 0.35),
                right: Math.round(fallbackWidth * 0.8),
                bottom: Math.round(fallbackHeight * 0.5),
              },
            },
        action: { type: "tap" },
        savedMetadataPath,
        savedScreenshotPath,
      } satisfies AnalyzeResponse);
    }

    if (process.env.ANALYZE_MOCK_ACTION === "scroll") {
      const conversationId = metadata.conversationId ?? randomUUID();
      const runId = randomUUID();

      return reply.send({
        conversationId,
        runId,
        conversationStatus: "waiting_interaction",
        answer: "临时 mock：请上下滑动页面",
        target: null,
        action: { type: "scroll" },
        savedMetadataPath,
        savedScreenshotPath,
      } satisfies AnalyzeResponse);
    }

    if (process.env.ANALYZE_MOCK_ACTION === "completed") {
      const conversationId = metadata.conversationId ?? randomUUID();
      const runId = randomUUID();

      return reply.send({
        conversationId,
        runId,
        conversationStatus: "completed",
        answer: "临时 mock：引导已完成",
        target: null,
        action: { type: "none" },
        savedMetadataPath,
        savedScreenshotPath,
      } satisfies AnalyzeResponse);
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
        return reply.status(400).send({
          code: "INVALID_REQUEST",
          message: error.message,
        } satisfies AnalyzeError);
      }
      if (error instanceof InvalidConversationStateError) {
        return reply.status(400).send({
          code: "INVALID_REQUEST",
          message: error.message,
        } satisfies AnalyzeError);
      }
      if (error instanceof DeviceBusyError) {
        return reply.status(400).send({
          code: "INVALID_REQUEST",
          message: error.message,
        } satisfies AnalyzeError);
      }
      throw error;
    }

    /* ---- A.1: create run + save snapshot + save user message ---- */
    const runId = randomUUID();
    const runIndex = await getNextRunIndex(db, conversationId);
    const previousRunId = await getLatestRunId(db, conversationId);

    /* ---- A.3: assemble + run agent + A.4: write back ---- */
    try {
      const createdRun = await createRun(db, {
        id: runId,
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
        runId: createdRun.id,
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
        runId: createdRun.id,
        messageType: metadata.messageType,
        text: metadata.messageText,
      });

      const agentInput = await assembleAgentRunInput({
        metadata,
        conversationId,
        runId: createdRun.id,
        goal,
        db,
        screenshotBuffer,
        screenshotMimeType,
      });

      const startedAt = Date.now();
      const runnerOutput = await agentRunner.run(agentInput);
      const latencyMs = Date.now() - startedAt;

      await completeRun(
        db,
        createdRun.id,
        conversationId,
        runnerOutput,
        latencyMs,
      );

      await saveAssistantGuidance(db, {
        conversationId,
        runId: createdRun.id,
        text: runnerOutput.answer,
      });

      return reply.send({
        conversationId,
        runId: createdRun.id,
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
      if (runId) {
        await failRun(
          db,
          runId,
          conversationId,
          "ANALYSIS_FAILED",
          error instanceof Error ? error.message : "Agent run failed.",
        );
      }

      return reply.status(502).send({
        code: "ANALYSIS_FAILED",
        message: error instanceof Error ? error.message : "Agent run failed.",
      } satisfies AnalyzeError);
    }
  };
}
