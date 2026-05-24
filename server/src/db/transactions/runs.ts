import { desc, eq } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";
import { sql } from "drizzle-orm";

import type { AgentRunOutput } from "../../agents/agent-runner.js";
import * as schema from "../../db/schema/index.js";

const { runs, conversations } = schema;

type Database = NodePgDatabase<typeof schema>;

export async function createRun(
  db: Database,
  params: {
    id: string;
    conversationId: string;
    runIndex: number;
    previousRunId: string | null;
    modelProvider: string;
    modelName: string;
    inputContextJson: Record<string, unknown>;
  },
) {
  const [created] = await db
    .insert(runs)
    .values({
      id: params.id,
      conversationId: params.conversationId,
      runIndex: params.runIndex,
      previousRunId: params.previousRunId,
      status: "running",
      modelProvider: params.modelProvider,
      modelName: params.modelName,
      inputContextJson: params.inputContextJson,
    })
    .returning({ id: runs.id });

  return created!;
}

export async function completeRun(
  db: Database,
  runId: string,
  conversationId: string,
  result: AgentRunOutput,
  latencyMs: number,
) {
  const now = new Date();

  await db
    .update(runs)
    .set({
      status: "completed",
      responseRawJson: result as unknown as Record<string, unknown>,
      resultAnswerText: result.answer,
      resultActionType: result.action.type,
      resultTargetLabel: result.target?.label ?? null,
      resultTargetBoundsJson: (result.target?.bounds ??
        null) as unknown as Record<string, unknown> | null,
      latencyMs,
      completedAt: now,
    })
    .where(eq(runs.id, runId));

  const newStatus = result.shouldContinue ? "waiting_interaction" : "completed";

  await db
    .update(conversations)
    .set({
      status: newStatus,
      updatedAt: now,
      version: sql`${conversations.version} + 1`,
      ...(newStatus === "waiting_interaction"
        ? { waitStartedAt: now, endedAt: null }
        : { waitStartedAt: null, endedAt: now }),
    })
    .where(eq(conversations.id, conversationId));
}

export async function failRun(
  db: Database,
  runId: string,
  conversationId: string,
  errorCode: string,
  errorMessage: string,
) {
  const now = new Date();

  await db
    .update(runs)
    .set({
      status: "failed",
      errorCode,
      errorMessage,
      completedAt: now,
    })
    .where(eq(runs.id, runId));

  await db
    .update(conversations)
    .set({
      status: "failed",
      waitStartedAt: null,
      endedAt: now,
      updatedAt: now,
      version: sql`${conversations.version} + 1`,
    })
    .where(eq(conversations.id, conversationId));
}

export async function getNextRunIndex(
  db: Database,
  conversationId: string,
): Promise<number> {
  const [row] = await db
    .select({
      maxIndex: schema.runs.runIndex,
    })
    .from(runs)
    .where(eq(runs.conversationId, conversationId))
    .orderBy(desc(schema.runs.runIndex))
    .limit(1);

  return (row?.maxIndex ?? 0) + 1;
}

export async function getLatestRunId(
  db: Database,
  conversationId: string,
): Promise<string | null> {
  const [row] = await db
    .select({ id: runs.id })
    .from(runs)
    .where(eq(runs.conversationId, conversationId))
    .orderBy(desc(schema.runs.runIndex))
    .limit(1);

  return row?.id ?? null;
}
