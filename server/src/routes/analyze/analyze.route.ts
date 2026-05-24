import type { FastifyInstance } from "fastify";

import type { AgentRunner } from "../../agents/agent-runner.js";
import type { DbClient } from "../../db/client.js";
import { createAnalyzeHandler } from "./analyze.handler.js";

export function registerAnalyzeRoutes(
  app: FastifyInstance,
  agentRunner: AgentRunner,
  dbClient?: DbClient,
) {
  app.post("/analyze", createAnalyzeHandler(agentRunner, dbClient));
}
