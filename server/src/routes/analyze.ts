import type { FastifyInstance } from "fastify";

export function registerAnalyzeRoute(app: FastifyInstance) {
  app.post("/analyze", async () => {
    return {
      status: "not_implemented",
      message: "Analyze pipeline has not been implemented yet.",
    };
  });
}
