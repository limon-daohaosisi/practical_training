import Fastify from "fastify";

import { registerAnalyzeRoute } from "./routes/analyze.js";
import { registerHealthRoute } from "./routes/health.js";

export function buildApp() {
  const app = Fastify({
    logger: true,
  });

  registerHealthRoute(app);
  registerAnalyzeRoute(app);

  return app;
}
