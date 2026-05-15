import { buildApp } from "./app.js";

const DEFAULT_HOST = "0.0.0.0";
const DEFAULT_PORT = 3000;

async function main() {
  const app = buildApp();
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
