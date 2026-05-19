import type { FastifyReply, FastifyRequest } from "fastify";

import type {
  CancelBody,
  CancelParams,
  CancelResponse,
} from "./cancel.schema.js";

export async function cancelHandler(
  request: FastifyRequest<{
    Params: CancelParams;
    Body: CancelBody;
  }>,
  reply: FastifyReply,
) {
  const response: CancelResponse = {
    conversationId: request.params.conversationId,
    status: "cancelled",
    closedReason: "user_exit",
  };

  return reply.send(response);
}
