import type { AgentRunOutput } from "../../agents/agent-runner.js";

export type AnalyzeExecutionResult = AgentRunOutput & {
  conversationId: string;
  runId: string;
  savedMetadataPath: string;
  savedScreenshotPath: string;
};
