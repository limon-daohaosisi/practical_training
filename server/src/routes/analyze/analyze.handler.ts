import type { FastifyReply, FastifyRequest } from "fastify";

import type { AnalyzeResponse } from "./analyze.schema.js";

export async function analyzeHandler(
  _request: FastifyRequest,
  reply: FastifyReply,
) {
  const response: AnalyzeResponse = {
    status: "not_implemented",
    message: "Analyze pipeline has not been implemented yet.",
  };

  return reply.send(response);
}
