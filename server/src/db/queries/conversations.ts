import { and, eq, inArray } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import * as schema from "../schema/index.js";

const { conversations } = schema;

type Database = NodePgDatabase<typeof schema>;

const ACTIVE_STATUSES = ["analyzing", "waiting_interaction"] as const;

export async function getActiveConversationByDevice(
  db: Database,
  deviceId: string,
) {
  const [row] = await db
    .select()
    .from(conversations)
    .where(
      and(
        eq(conversations.deviceId, deviceId),
        inArray(conversations.status, ACTIVE_STATUSES),
      ),
    )
    .limit(1);

  return row ?? null;
}

export async function getConversationByIdAndDevice(
  db: Database,
  conversationId: string,
  deviceId: string,
) {
  const [row] = await db
    .select()
    .from(conversations)
    .where(
      and(
        eq(conversations.id, conversationId),
        eq(conversations.deviceId, deviceId),
      ),
    )
    .limit(1);

  return row ?? null;
}

export async function isDeviceBusy(
  db: Database,
  deviceId: string,
): Promise<boolean> {
  const [row] = await db
    .select({ id: conversations.id })
    .from(conversations)
    .where(
      and(
        eq(conversations.deviceId, deviceId),
        eq(conversations.status, "analyzing"),
      ),
    )
    .limit(1);

  return row !== undefined;
}
