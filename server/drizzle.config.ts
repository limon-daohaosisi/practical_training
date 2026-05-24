import { config as loadEnv } from "dotenv";
import { defineConfig } from "drizzle-kit";

loadEnv();

const databaseUrl = process.env.DATABASE_URL;

if (!databaseUrl) {
  throw new Error(
    "DATABASE_URL is required to generate or run Drizzle migrations.",
  );
}

export default defineConfig({
  dialect: "postgresql",
  schema: "./src/db/schema/index.ts",
  out: "./drizzle/migrations",
  dbCredentials: {
    url: databaseUrl,
  },
});
