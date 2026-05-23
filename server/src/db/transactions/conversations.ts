import { and, eq, sql } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import * as schema from "../../db/schema/index.js";

const { conversations } = schema;

type Database = NodePgDatabase<typeof schema>;

export class ConversationNotFoundError extends Error {
  constructor(conversationId: string) {
    super(`Conversation ${conversationId} not found for this device`);
    this.name = "ConversationNotFoundError";
  }
}

export class InvalidConversationStateError extends Error {
  constructor(
    conversationId: string,
    currentStatus: string,
  ) {
    super(
      `Conversation ${conversationId} is in status '${currentStatus}', expected 'waiting_interaction'`,
    );
    this.name = "InvalidConversationStateError";
  }
}

export async function startConversationFromSpeech(
  db: Database,
  deviceId: string,
  appPackageName: string,
) {
  await db
    .update(conversations)
    .set({
      status: "cancelled",
      closedReason: "new_speech",
      endedAt: new Date(),
    })
    .where(
      and(
        eq(conversations.deviceId, deviceId),
        eq(conversations.status, "waiting_interaction"),
      ),
    );

  const [created] = await db
    .insert(conversations)
    .values({
      deviceId,
      appPackageName,
      status: "analyzing",
    })
    .returning();

  return created!;
}

export async function continueConversationFromObservedEvent(
  db: Database,
  deviceId: string,
  conversationId: string,
) {
  const existing = await db
    .select()
    .from(conversations)
    .where(
      and(
        eq(conversations.id, conversationId),
        eq(conversations.deviceId, deviceId),
      ),
    )
    .limit(1);

  const conv = existing[0];
  if (!conv) {
    throw new ConversationNotFoundError(conversationId);
  }

  if (conv.status !== "waiting_interaction") {
    throw new InvalidConversationStateError(conversationId, conv.status);
  }

  const [updated] = await db
    .update(conversations)
    .set({
      status: "analyzing",
      version: sql`${conversations.version} + 1`,
    })
    .where(
      and(
        eq(conversations.id, conversationId),
        eq(conversations.deviceId, deviceId),
      ),
    )
    .returning();

  return updated!;
}

export async function cancelConversationByUserExit(
  db: Database,
  deviceId: string,
  conversationId: string,
) {
  const existing = await db
    .select()
    .from(conversations)
    .where(
      and(
        eq(conversations.id, conversationId),
        eq(conversations.deviceId, deviceId),
      ),
    )
    .limit(1);

  const conv = existing[0];
  if (!conv) {
    throw new ConversationNotFoundError(conversationId);
  }

  await db
    .update(conversations)
    .set({
      status: "cancelled",
      closedReason: "user_exit",
      endedAt: new Date(),
    })
    .where(eq(conversations.id, conversationId));
}
