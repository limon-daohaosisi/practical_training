import type { FastifyReply, FastifyRequest } from "fastify";

import { persistAnalyzeDebugPayload } from "../../services/analyze-debug-storage.js";
import type { AnalyzeError, AnalyzeResponse } from "./analyze.schema.js";

export async function analyzeHandler(
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

    const response: AnalyzeResponse = {
      answer: "Analyze pipeline placeholder response.",
      target: null,
      action: {
        type: "none",
      },
      savedMetadataPath: persisted.savedMetadataPath,
      savedScreenshotPath: persisted.savedScreenshotPath,
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
}
