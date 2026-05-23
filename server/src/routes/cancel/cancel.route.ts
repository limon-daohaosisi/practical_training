import type { FastifyInstance } from "fastify";

import type { DbClient } from "../../db/client.js";
import { createCancelHandler } from "./cancel.handler.js";

export function registerCancelRoutes(
  app: FastifyInstance,
  dbClient?: DbClient,
) {
  app.post(
    "/conversations/:conversationId/cancel",
    createCancelHandler(dbClient),
  );
}
