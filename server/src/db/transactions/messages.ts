import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import * as schema from "../../db/schema/index.js";

const { messages } = schema;

type Database = NodePgDatabase<typeof schema>;

export async function saveUserMessage(
  db: Database,
  params: {
    conversationId: string;
    runId: string;
    messageType: "speech_text" | "observed_click" | "observed_scroll";
    text: string | null;
  },
) {
  await db.insert(messages).values({
    conversationId: params.conversationId,
    runId: params.runId,
    role: "user",
    messageType: params.messageType,
    text: params.text,
  });
}

export async function saveAssistantGuidance(
  db: Database,
  params: {
    conversationId: string;
    runId: string;
    text: string;
  },
) {
  await db.insert(messages).values({
    conversationId: params.conversationId,
    runId: params.runId,
    role: "assistant",
    messageType: "guidance",
    text: params.text,
  });
}
