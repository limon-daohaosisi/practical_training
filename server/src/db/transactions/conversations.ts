import { and, eq, sql } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";
import { DatabaseError } from "pg";

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
  constructor(conversationId: string, currentStatus: string) {
    super(
      `Conversation ${conversationId} is in status '${currentStatus}', expected 'waiting_interaction'`,
    );
    this.name = "InvalidConversationStateError";
  }
}

export class DeviceBusyError extends Error {
  constructor(deviceId: string) {
    super(`Device ${deviceId} already has an active analysis in progress.`);
    this.name = "DeviceBusyError";
  }
}

export async function startConversationFromSpeech(
  db: Database,
  deviceId: string,
  appPackageName: string,
) {
  const now = new Date();

  try {
    return await db.transaction(async (tx) => {
      await tx
        .update(conversations)
        .set({
          status: "cancelled",
          closedReason: "new_speech",
          endedAt: now,
          updatedAt: now,
          version: sql`${conversations.version} + 1`,
        })
        .where(
          and(
            eq(conversations.deviceId, deviceId),
            eq(conversations.status, "waiting_interaction"),
          ),
        );

      const [created] = await tx
        .insert(conversations)
        .values({
          deviceId,
          appPackageName,
          status: "analyzing",
          updatedAt: now,
        })
        .returning();

      return created!;
    });
  } catch (error) {
    if (
      error instanceof DatabaseError &&
      error.code === "23505" &&
      error.constraint === "conversations_active_device_idx"
    ) {
      throw new DeviceBusyError(deviceId);
    }
    throw error;
  }
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

  const now = new Date();
  const [updated] = await db
    .update(conversations)
    .set({
      status: "analyzing",
      waitStartedAt: null,
      updatedAt: now,
      version: sql`${conversations.version} + 1`,
    })
    .where(
      and(
        eq(conversations.id, conversationId),
        eq(conversations.deviceId, deviceId),
        eq(conversations.status, "waiting_interaction"),
        eq(conversations.version, conv.version),
      ),
    )
    .returning();

  if (!updated) {
    throw new InvalidConversationStateError(conversationId, conv.status);
  }

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

  const now = new Date();

  await db
    .update(conversations)
    .set({
      status: "cancelled",
      closedReason: "user_exit",
      endedAt: now,
      updatedAt: now,
      version: sql`${conversations.version} + 1`,
    })
    .where(
      and(
        eq(conversations.id, conversationId),
        eq(conversations.deviceId, deviceId),
      ),
    );
}
