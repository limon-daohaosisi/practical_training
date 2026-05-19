import { randomUUID } from "node:crypto";

import type { FastifyReply, FastifyRequest } from "fastify";

import type { AgentRunner } from "../../agents/agent-runner.js";
import { persistAnalyzeDebugPayload } from "../../services/analyze-debug-storage.js";
import type {
  AnalyzeError,
  AnalyzeMetadata,
  AnalyzeResponse,
} from "./analyze.schema.js";
import type { AnalyzeExecutionResult } from "./analyze.types.js";
import { analyzeMetadataSchema } from "./analyze.schema.js";

export function createAnalyzeHandler(agentRunner: AgentRunner) {
  return async function analyzeHandler(
    request: FastifyRequest,
    reply: FastifyReply,
  ) {
    const contentType = request.headers["content-type"] ?? "";
    const body = Buffer.isBuffer(request.body) ? request.body : null;

    if (!contentType.includes("multipart/form-data") || body === null) {
      const error: AnalyzeError = {
        code: "INVALID_REQUEST",
        message: "metadata and screenshot are required.",
      };

      return reply.status(400).send(error);
    }

    try {
      const persisted = await persistAnalyzeDebugPayload({
        contentType,
        body,
      });
      const metadata = analyzeMetadataSchema.parse(
        JSON.parse(persisted.metadataJson),
      ) as AnalyzeMetadata;
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
        // Current placeholder implementation does not load persisted
        // conversation history yet. The query/transaction layer should later
        // populate this from the current conversation only.
        recentMessages: [],
        screenshot: {
          buffer: persisted.screenshotBytes,
          mimeType: persisted.screenshotMimeType,
        },
      });

      const result: AnalyzeExecutionResult = {
        conversationId,
        runId,
        ...runnerOutput,
        savedMetadataPath: persisted.savedMetadataPath,
        savedScreenshotPath: persisted.savedScreenshotPath,
      };

      const response: AnalyzeResponse = {
        conversationId: result.conversationId,
        runId: result.runId,
        conversationStatus: result.shouldContinue
          ? "waiting_interaction"
          : "completed",
        answer: result.answer,
        target: result.target,
        action: result.action,
        savedMetadataPath: result.savedMetadataPath,
        savedScreenshotPath: result.savedScreenshotPath,
      };

      return reply.send(response);
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "metadata and screenshot are required.";
      const response: AnalyzeError = {
        code: "INVALID_REQUEST",
        message,
      };
      return reply.status(400).send(response);
    }
  };
}
