import type { FastifyInstance } from "fastify";

import { healthHandler } from "./health.handler.js";

export function registerHealthRoutes(app: FastifyInstance) {
  app.get("/health", healthHandler);
}
