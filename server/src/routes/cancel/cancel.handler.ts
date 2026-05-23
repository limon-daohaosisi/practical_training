import type { FastifyReply, FastifyRequest } from "fastify";

import type { DbClient } from "../../db/client.js";
import {
  ConversationNotFoundError,
  cancelConversationByUserExit,
} from "../../db/transactions/conversations.js";
import type {
  CancelBody,
  CancelParams,
  CancelResponse,
} from "./cancel.schema.js";

export function createCancelHandler(dbClient?: DbClient) {
  return async function cancelHandler(
    request: FastifyRequest<{
      Params: CancelParams;
      Body: CancelBody;
    }>,
    reply: FastifyReply,
  ) {
    if (dbClient) {
      try {
        await cancelConversationByUserExit(
          dbClient.db,
          request.body.deviceId,
          request.params.conversationId,
        );
      } catch (error) {
        if (error instanceof ConversationNotFoundError) {
          return reply.status(404).send({
            conversationId: request.params.conversationId,
            status: "cancelled" as const,
            closedReason: "user_exit" as const,
          });
        }
        throw error;
      }
    }

    const response: CancelResponse = {
      conversationId: request.params.conversationId,
      status: "cancelled",
      closedReason: "user_exit",
    };

    return reply.send(response);
  };
}
