import Fastify from "fastify";

import type { AgentRunner } from "./agents/agent-runner.js";
import { mockAgentRunner } from "./agents/mock-agent-runner.js";
import { registerAnalyzeRoutes } from "./routes/analyze/analyze.route.js";
import { registerCancelRoutes } from "./routes/cancel/cancel.route.js";
import { registerHealthRoutes } from "./routes/health/health.route.js";

type BuildAppOptions = {
  agentRunner?: AgentRunner;
};

export function buildApp(options: BuildAppOptions = {}) {
  const app = Fastify({
    logger: true,
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
  registerAnalyzeRoutes(app, agentRunner);
  registerCancelRoutes(app);

  return app;
}
