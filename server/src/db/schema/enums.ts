import { pgEnum } from "drizzle-orm/pg-core";

export const conversationStatusEnum = pgEnum("conversation_status", [
  "analyzing",
  "waiting_interaction",
  "completed",
  "failed",
  "cancelled",
  "expired",
]);

export const runStatusEnum = pgEnum("run_status", [
  "received",
  "running",
  "completed",
  "failed",
]);

export const runResultActionTypeEnum = pgEnum("run_result_action_type", [
  "tap",
  "scroll",
  "input",
  "wait",
  "none",
]);

export const messageRoleEnum = pgEnum("message_role", ["user", "assistant"]);

export const messageTypeEnum = pgEnum("message_type", [
  "speech_text",
  "observed_click",
  "observed_scroll",
  "guidance",
]);
