import {
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
import { runs } from "./runs.js";

export const screenSnapshots = pgTable(
  "screen_snapshots",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    conversationId: uuid("conversation_id")
      .notNull()
      .references(() => conversations.id, { onDelete: "cascade" }),
    runId: uuid("run_id")
      .notNull()
      .references(() => runs.id, { onDelete: "cascade" }),
    packageName: text("package_name").notNull(),
    activityName: text("activity_name"),
    screenWidth: integer("screen_width").notNull(),
    screenHeight: integer("screen_height").notNull(),
    imageWidth: integer("image_width").notNull(),
    imageHeight: integer("image_height").notNull(),
    nodesJson: jsonb("nodes_json").notNull(),
    nodeCount: integer("node_count").notNull(),
    capturedAt: timestamp("captured_at", {
      withTimezone: true,
      mode: "date",
    }).notNull(),
    createdAt: timestamp("created_at", {
      withTimezone: true,
      mode: "date",
    })
      .defaultNow()
      .notNull(),
  },
  (table) => [
    uniqueIndex("screen_snapshots_run_id_uidx").on(table.runId),
    index("screen_snapshots_conversation_id_created_at_idx").on(
      table.conversationId,
      table.createdAt,
    ),
  ],
);
