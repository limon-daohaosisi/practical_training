import type { FastifyInstance } from "fastify";

import type { AgentRunner } from "../../agents/agent-runner.js";
import { createAnalyzeHandler } from "./analyze.handler.js";

export function registerAnalyzeRoutes(
  app: FastifyInstance,
  agentRunner: AgentRunner,
) {
  app.post("/analyze", createAnalyzeHandler(agentRunner));
}
