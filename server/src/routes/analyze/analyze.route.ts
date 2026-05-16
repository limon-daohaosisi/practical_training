import type { FastifyInstance } from "fastify";

import { analyzeHandler } from "./analyze.handler.js";

export function registerAnalyzeRoutes(app: FastifyInstance) {
  app.post("/analyze", analyzeHandler);
}
