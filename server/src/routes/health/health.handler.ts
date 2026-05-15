import type { FastifyReply, FastifyRequest } from "fastify";

import type { HealthResponse } from "./health.schema.js";

export async function healthHandler(
  _request: FastifyRequest,
  reply: FastifyReply,
) {
  const response: HealthResponse = {
    status: "ok",
    service: "guide-assistant-server",
  };

  return reply.send(response);
}
