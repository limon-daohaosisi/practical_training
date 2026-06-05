import Fastify from "fastify";

import type { AgentRunner } from "./agents/agent-runner.js";
import { mockAgentRunner } from "./agents/mock-agent-runner.js";
import type { DbClient } from "./db/client.js";
import { registerAnalyzeRoutes } from "./routes/analyze/analyze.route.js";
import { registerCancelRoutes } from "./routes/cancel/cancel.route.js";
import { registerHealthRoutes } from "./routes/health/health.route.js";

const DEFAULT_BODY_LIMIT_BYTES = 10 * 1024 * 1024;

type BuildAppOptions = {
  agentRunner?: AgentRunner;
  dbClient?: DbClient;
};

export function buildApp(options: BuildAppOptions = {}) {
  const app = Fastify({
    logger: true,
    bodyLimit: DEFAULT_BODY_LIMIT_BYTES,
  });
  const agentRunner = options.agentRunner ?? mockAgentRunner;

  app.addContentTypeParser(
    /^multipart\/form-data/i,
    { parseAs: "buffer" },
    (_request, body, done) => {
      done(null, body);
    },
  );

  registerHealthRoutes(app);
  registerAnalyzeRoutes(app, agentRunner, options.dbClient);
  registerCancelRoutes(app, options.dbClient);

  return app;
}
