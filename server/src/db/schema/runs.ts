import {
  type AnyPgColumn,
  index,
  integer,
  jsonb,
  pgTable,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core";

import { conversations } from "./conversations.js";
import { runResultActionTypeEnum, runStatusEnum } from "./enums.js";

export const runs = pgTable(
  "runs",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    conversationId: uuid("conversation_id")
      .notNull()
      .references(() => conversations.id, { onDelete: "cascade" }),
    runIndex: integer("run_index").notNull(),
    previousRunId: uuid("previous_run_id").references(
      (): AnyPgColumn => runs.id,
      {
        onDelete: "set null",
      },
    ),
    status: runStatusEnum("status").notNull(),
    observedAt: timestamp("observed_at", {
      withTimezone: true,
      mode: "date",
    }),
    modelProvider: text("model_provider").notNull(),
    modelName: text("model_name").notNull(),
    promptVersion: text("prompt_version"),
    inputContextJson: jsonb("input_context_json").notNull(),
    responseRawJson: jsonb("response_raw_json"),
    resultAnswerText: text("result_answer_text"),
    resultActionType: runResultActionTypeEnum("result_action_type"),
    resultTargetLabel: text("result_target_label"),
    resultTargetBoundsJson: jsonb("result_target_bounds_json"),
    latencyMs: integer("latency_ms"),
    errorCode: text("error_code"),
    errorMessage: text("error_message"),
    startedAt: timestamp("started_at", {
      withTimezone: true,
      mode: "date",
    })
      .defaultNow()
      .notNull(),
    completedAt: timestamp("completed_at", {
      withTimezone: true,
      mode: "date",
    }),
    createdAt: timestamp("created_at", {
      withTimezone: true,
      mode: "date",
    })
      .defaultNow()
      .notNull(),
  },
  (table) => [
    uniqueIndex("runs_conversation_id_run_index_uidx").on(
      table.conversationId,
      table.runIndex,
    ),
    index("runs_previous_run_id_idx").on(table.previousRunId),
    index("runs_conversation_id_created_at_idx").on(
      table.conversationId,
      table.createdAt,
    ),
  ],
);
