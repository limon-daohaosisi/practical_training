import { config as loadEnv } from "dotenv";

import { createAgentRunnerFromEnv } from "./agents/create-agent-runner.js";
import { buildApp } from "./app.js";
import { createDbClient } from "./db/client.js";

const DEFAULT_HOST = "0.0.0.0";
const DEFAULT_PORT = 3000;

loadEnv();

async function main() {
  const agentRunner = createAgentRunnerFromEnv();
  const dbClient = createDbClient();
  const app = buildApp({ agentRunner, dbClient });
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
