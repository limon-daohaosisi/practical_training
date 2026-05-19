CREATE TYPE "public"."conversation_status" AS ENUM('analyzing', 'waiting_interaction', 'completed', 'failed', 'cancelled', 'expired');
CREATE TYPE "public"."message_role" AS ENUM('user', 'assistant');
CREATE TYPE "public"."message_type" AS ENUM('speech_text', 'observed_click', 'observed_scroll', 'guidance');
CREATE TYPE "public"."run_result_action_type" AS ENUM('tap', 'scroll', 'input', 'wait', 'none');
CREATE TYPE "public"."run_status" AS ENUM('received', 'running', 'completed', 'failed');

CREATE TABLE "conversations" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "device_id" text NOT NULL,
  "app_package_name" text NOT NULL,
  "status" "conversation_status" NOT NULL,
  "wait_started_at" timestamp with time zone,
  "closed_reason" text,
  "ended_at" timestamp with time zone,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL,
  "version" integer DEFAULT 1 NOT NULL
);

CREATE TABLE "runs" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "conversation_id" uuid NOT NULL,
  "run_index" integer NOT NULL,
  "previous_run_id" uuid,
  "status" "run_status" NOT NULL,
  "observed_at" timestamp with time zone,
  "model_provider" text NOT NULL,
  "model_name" text NOT NULL,
  "prompt_version" text,
  "input_context_json" jsonb NOT NULL,
  "response_raw_json" jsonb,
  "result_answer_text" text,
  "result_action_type" "run_result_action_type",
  "result_target_label" text,
  "result_target_bounds_json" jsonb,
  "latency_ms" integer,
  "error_code" text,
  "error_message" text,
  "started_at" timestamp with time zone DEFAULT now() NOT NULL,
  "completed_at" timestamp with time zone,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "messages" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "conversation_id" uuid NOT NULL,
  "run_id" uuid NOT NULL,
  "role" "message_role" NOT NULL,
  "message_type" "message_type" NOT NULL,
  "text" text,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "screen_snapshots" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "conversation_id" uuid NOT NULL,
  "run_id" uuid NOT NULL,
  "package_name" text NOT NULL,
  "activity_name" text,
  "screen_width" integer NOT NULL,
  "screen_height" integer NOT NULL,
  "image_width" integer NOT NULL,
  "image_height" integer NOT NULL,
  "nodes_json" jsonb NOT NULL,
  "node_count" integer NOT NULL,
  "captured_at" timestamp with time zone NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE "runs"
  ADD CONSTRAINT "runs_conversation_id_conversations_id_fk"
  FOREIGN KEY ("conversation_id")
  REFERENCES "public"."conversations"("id")
  ON DELETE cascade
  ON UPDATE no action;

ALTER TABLE "runs"
  ADD CONSTRAINT "runs_previous_run_id_runs_id_fk"
  FOREIGN KEY ("previous_run_id")
  REFERENCES "public"."runs"("id")
  ON DELETE set null
  ON UPDATE no action;

ALTER TABLE "messages"
  ADD CONSTRAINT "messages_conversation_id_conversations_id_fk"
  FOREIGN KEY ("conversation_id")
  REFERENCES "public"."conversations"("id")
  ON DELETE cascade
  ON UPDATE no action;

ALTER TABLE "messages"
  ADD CONSTRAINT "messages_run_id_runs_id_fk"
  FOREIGN KEY ("run_id")
  REFERENCES "public"."runs"("id")
  ON DELETE cascade
  ON UPDATE no action;

ALTER TABLE "screen_snapshots"
  ADD CONSTRAINT "screen_snapshots_conversation_id_conversations_id_fk"
  FOREIGN KEY ("conversation_id")
  REFERENCES "public"."conversations"("id")
  ON DELETE cascade
  ON UPDATE no action;

ALTER TABLE "screen_snapshots"
  ADD CONSTRAINT "screen_snapshots_run_id_runs_id_fk"
  FOREIGN KEY ("run_id")
  REFERENCES "public"."runs"("id")
  ON DELETE cascade
  ON UPDATE no action;

CREATE INDEX "conversations_device_id_status_idx" ON "conversations" USING btree ("device_id", "status");
CREATE INDEX "conversations_updated_at_idx" ON "conversations" USING btree ("updated_at" desc);
CREATE UNIQUE INDEX "conversations_active_device_idx" ON "conversations" USING btree ("device_id") WHERE "conversations"."status" in ('analyzing', 'waiting_interaction');

CREATE UNIQUE INDEX "runs_conversation_id_run_index_uidx" ON "runs" USING btree ("conversation_id", "run_index");
CREATE INDEX "runs_previous_run_id_idx" ON "runs" USING btree ("previous_run_id");
CREATE INDEX "runs_conversation_id_created_at_idx" ON "runs" USING btree ("conversation_id", "created_at");

CREATE INDEX "messages_conversation_id_created_at_idx" ON "messages" USING btree ("conversation_id", "created_at");
CREATE INDEX "messages_run_id_created_at_idx" ON "messages" USING btree ("run_id", "created_at");

CREATE UNIQUE INDEX "screen_snapshots_run_id_uidx" ON "screen_snapshots" USING btree ("run_id");
CREATE INDEX "screen_snapshots_conversation_id_created_at_idx" ON "screen_snapshots" USING btree ("conversation_id", "created_at");
