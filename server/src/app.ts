import Fastify from "fastify";

import { registerAnalyzeRoutes } from "./routes/analyze/analyze.route.js";
import { registerHealthRoutes } from "./routes/health/health.route.js";

export function buildApp() {
  const app = Fastify({
    logger: true,
  });

  app.addContentTypeParser(
    /^multipart\/form-data/i,
    { parseAs: "buffer" },
    (_request, body, done) => {
      done(null, body);
    },
  );

  registerHealthRoutes(app);
  registerAnalyzeRoutes(app);

  return app;
}
