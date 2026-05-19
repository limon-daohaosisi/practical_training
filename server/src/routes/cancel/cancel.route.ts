import type { FastifyInstance } from "fastify";

import { cancelHandler } from "./cancel.handler.js";

export function registerCancelRoutes(app: FastifyInstance) {
  app.post("/conversations/:conversationId/cancel", cancelHandler);
}
