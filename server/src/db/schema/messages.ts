import { index, pgTable, text, timestamp, uuid } from "drizzle-orm/pg-core";

import { conversations } from "./conversations.js";
import { messageRoleEnum, messageTypeEnum } from "./enums.js";
import { runs } from "./runs.js";

export const messages = pgTable(
  "messages",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    conversationId: uuid("conversation_id")
      .notNull()
      .references(() => conversations.id, { onDelete: "cascade" }),
    runId: uuid("run_id")
      .notNull()
      .references(() => runs.id, { onDelete: "cascade" }),
    role: messageRoleEnum("role").notNull(),
    messageType: messageTypeEnum("message_type").notNull(),
    text: text("text"),
    createdAt: timestamp("created_at", {
      withTimezone: true,
      mode: "date",
    })
      .defaultNow()
      .notNull(),
  },
  (table) => [
    index("messages_conversation_id_created_at_idx").on(
      table.conversationId,
      table.createdAt,
    ),
    index("messages_run_id_created_at_idx").on(table.runId, table.createdAt),
  ],
);
