import type { AgentRunner } from "./agent-runner.js";
import { mockAgentRunner } from "./mock-agent-runner.js";
import { createOpenAiCompatibleAgentRunnerFromEnv } from "./openai-compatible-agent-runner.js";
import { createOpenAiCompatibleClientFromEnv } from "../plugins/openai-compatible-client.js";

export function createAgentRunnerFromEnv(
  env: NodeJS.ProcessEnv = process.env,
): AgentRunner {
  const runnerType = env.AGENT_RUNNER ?? "mock";

  if (runnerType === "mock") {
    return mockAgentRunner;
  }

  if (runnerType === "openai-compatible") {
    const modelClient = createOpenAiCompatibleClientFromEnv(env);
    return createOpenAiCompatibleAgentRunnerFromEnv(modelClient, env);
  }

  throw new Error(
    `Unsupported AGENT_RUNNER "${runnerType}". Use "mock" or "openai-compatible".`,
  );
}
