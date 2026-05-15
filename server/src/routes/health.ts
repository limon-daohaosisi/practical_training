import type { FastifyInstance } from "fastify";

export function registerHealthRoute(app: FastifyInstance) {
  app.get("/health", async () => {
    return {
      status: "ok",
      service: "guide-assistant-server",
    };
  });
}
