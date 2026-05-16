import Fastify from "fastify";

import { registerAnalyzeRoutes } from "./routes/analyze/analyze.route.js";
import { registerHealthRoutes } from "./routes/health/health.route.js";

export function buildApp() {
  const app = Fastify({
    logger: true,
  });

  registerHealthRoutes(app);
  registerAnalyzeRoutes(app);

  return app;
}
