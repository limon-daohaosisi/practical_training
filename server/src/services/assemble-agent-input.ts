import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import type { AgentRunInput } from "../agents/agent-runner.js";
import * as schema from "../db/schema/index.js";
import { getConversationGoal, getRecentMessagesByConversation } from "../db/queries/messages.js";
import type { AnalyzeMetadata } from "../routes/analyze/analyze.schema.js";

type Database = NodePgDatabase<typeof schema>;

export function resolveGoalForSpeech(metadata: AnalyzeMetadata): string {
  return metadata.messageText?.trim() ?? "";
}

export async function resolveGoalForObserved(
  db: Database,
  conversationId: string,
): Promise<string> {
  return getConversationGoal(db, conversationId);
}

export type AssembleInputParams = {
  metadata: AnalyzeMetadata;
  conversationId: string;
  runId: string;
  goal: string;
  db: Database;
  screenshotBuffer: Buffer;
  screenshotMimeType: string;
};

export async function assembleAgentRunInput(
  params: AssembleInputParams,
): Promise<AgentRunInput> {
  const recentMessages = await getRecentMessagesByConversation(
    params.db,
    params.conversationId,
  );

  return {
    conversationId: params.conversationId,
    runId: params.runId,
    goal: params.goal,
    messageType: params.metadata.messageType,
    messageText: params.metadata.messageText,
    currentSnapshot: {
      packageName: params.metadata.packageName,
      activityName: params.metadata.activityName ?? null,
      screenWidth: params.metadata.screenWidth,
      screenHeight: params.metadata.screenHeight,
      imageWidth: params.metadata.imageWidth,
      imageHeight: params.metadata.imageHeight,
      nodes: params.metadata.nodes,
    },
    recentMessages,
    screenshot: {
      buffer: params.screenshotBuffer,
      mimeType: params.screenshotMimeType,
    },
  };
}
