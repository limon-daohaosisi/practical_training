import { describe, expect, it } from "vitest";

import { createAgentRunnerFromEnv } from "../agents/create-agent-runner.js";

describe("createAgentRunnerFromEnv", () => {
  it("uses the mock runner by default", () => {
    expect(createAgentRunnerFromEnv({}).constructor.name).toBe(
      "MockAgentRunner",
    );
  });

  it("fails fast when openai-compatible config is missing", () => {
    expect(() =>
      createAgentRunnerFromEnv({ AGENT_RUNNER: "openai-compatible" }),
    ).toThrow("OPENAI_API_KEY is required");
  });

  it("creates the openai-compatible runner when config is present", () => {
    const runner = createAgentRunnerFromEnv({
      AGENT_RUNNER: "openai-compatible",
      OPENAI_API_KEY: "app-key",
      OPENAI_BASE_URL: "https://api-ai.vivo.com.cn/v1",
      OPENAI_MODEL: "Volc-DeepSeek-V3.2",
    });

    expect(runner.constructor.name).toBe("OpenAiCompatibleAgentRunner");
  });
});
