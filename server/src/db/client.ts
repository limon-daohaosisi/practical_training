import { drizzle } from "drizzle-orm/node-postgres";
import { Pool } from "pg";

import * as schema from "./schema/index.js";

export function createDbClient(databaseUrl = process.env.DATABASE_URL) {
  if (!databaseUrl) {
    throw new Error(
      "DATABASE_URL is required to initialize the database client.",
    );
  }

  const pool = new Pool({
    connectionString: databaseUrl,
  });

  return {
    pool,
    db: drizzle(pool, { schema }),
  };
}

export type DbClient = ReturnType<typeof createDbClient>;
