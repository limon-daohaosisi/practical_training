import {
  index,
  integer,
  pgTable,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core";
import { sql } from "drizzle-orm";

import { conversationStatusEnum } from "./enums.js";

export const conversations = pgTable(
  "conversations",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    deviceId: text("device_id").notNull(),
    appPackageName: text("app_package_name").notNull(),
    status: conversationStatusEnum("status").notNull(),
    waitStartedAt: timestamp("wait_started_at", {
      withTimezone: true,
      mode: "date",
    }),
    closedReason: text("closed_reason"),
    endedAt: timestamp("ended_at", {
      withTimezone: true,
      mode: "date",
    }),
    createdAt: timestamp("created_at", {
      withTimezone: true,
      mode: "date",
    })
      .defaultNow()
      .notNull(),
    updatedAt: timestamp("updated_at", {
      withTimezone: true,
      mode: "date",
    })
      .defaultNow()
      .notNull(),
    version: integer("version").default(1).notNull(),
  },
  (table) => [
    index("conversations_device_id_status_idx").on(
      table.deviceId,
      table.status,
    ),
    index("conversations_updated_at_idx").on(sql`${table.updatedAt} desc`),
    uniqueIndex("conversations_active_device_idx")
      .on(table.deviceId)
      .where(sql`${table.status} in ('analyzing', 'waiting_interaction')`),
  ],
);
