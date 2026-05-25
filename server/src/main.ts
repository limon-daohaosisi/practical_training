import { config as loadEnv } from "dotenv";

import { buildApp } from "./app.js";
import { createDbClient } from "./db/client.js";

const DEFAULT_HOST = "0.0.0.0";
const DEFAULT_PORT = 3000;

loadEnv();

async function main() {
  const shouldUseMockAnalyze =
    process.env.ANALYZE_MOCK_ACTION === "tap" ||
    process.env.ANALYZE_MOCK_ACTION === "scroll" ||
    process.env.ANALYZE_MOCK_ACTION === "completed";
  const dbClient = shouldUseMockAnalyze ? undefined : createDbClient();
  const app = buildApp({ dbClient });
  const port = Number(process.env.PORT ?? DEFAULT_PORT);
  const host = process.env.HOST ?? DEFAULT_HOST;

  try {
    await app.listen({ host, port });
  } catch (error) {
    app.log.error(error);
    process.exit(1);
  }
}

void main();
