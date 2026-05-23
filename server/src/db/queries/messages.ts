import { asc, eq } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import type { AgentContextMessage } from "../../agents/agent-runner.js";
import * as schema from "../schema/index.js";

const { messages, conversations, runs } = schema;

type Database = NodePgDatabase<typeof schema>;

export async function getRecentMessagesByConversation(
  db: Database,
  conversationId: string,
  limit = 20,
): Promise<AgentContextMessage[]> {
  const rows = await db
    .select({
      id: messages.id,
      conversationId: messages.conversationId,
      conversationStatus: conversations.status,
      closedReason: conversations.closedReason,
      createdAt: messages.createdAt,
      role: messages.role,
      messageType: messages.messageType,
      text: messages.text,
      resultActionType: runs.resultActionType,
      resultTargetLabel: runs.resultTargetLabel,
      resultTargetBoundsJson: runs.resultTargetBoundsJson,
    })
    .from(messages)
    .innerJoin(conversations, eq(messages.conversationId, conversations.id))
    .leftJoin(runs, eq(messages.runId, runs.id))
    .where(eq(messages.conversationId, conversationId))
    .orderBy(asc(messages.createdAt))
    .limit(limit);

  return rows.map((row) => ({
    conversationId: row.conversationId,
    conversationStatus: row.conversationStatus,
    closedReason: row.closedReason,
    createdAt: row.createdAt.toISOString(),
    role: row.role,
    messageType: row.messageType,
    text: row.text,
    guidance:
      row.role === "assistant" &&
      row.messageType === "guidance" &&
      row.resultActionType &&
      (row.resultActionType === "tap" ||
        row.resultActionType === "scroll" ||
        row.resultActionType === "none")
        ? {
            actionType: row.resultActionType as "tap" | "scroll" | "none",
            targetLabel: row.resultTargetLabel,
            targetBounds: row.resultTargetBoundsJson as {
              left: number;
              top: number;
              right: number;
              bottom: number;
            } | null,
          }
        : null,
  }));
}

export async function getConversationGoal(
  db: Database,
  conversationId: string,
): Promise<string> {
  const [row] = await db
    .select({ text: messages.text })
    .from(messages)
    .where(
      eq(messages.conversationId, conversationId),
    )
    .orderBy(asc(messages.createdAt))
    .limit(1);

  return row?.text ?? "";
}
