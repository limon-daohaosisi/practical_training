import { describe, expect, it } from "vitest";

import type { AgentRunInput } from "../agents/agent-runner.js";
import type {
  CreateChatCompletionInput,
  CreateChatCompletionOutput,
  ModelClient,
} from "../agents/model-client.js";
import { OpenAiCompatibleAgentRunner } from "../agents/openai-compatible-agent-runner.js";
import { fallbackAgentRunOutput } from "../services/agent-output-parser.js";

class FakeModelClient implements ModelClient {
  lastInput: CreateChatCompletionInput | null = null;

  constructor(private readonly content: string) {}

  async createChatCompletion(
    input: CreateChatCompletionInput,
  ): Promise<CreateChatCompletionOutput> {
    this.lastInput = input;
    return {
      content: this.content,
      raw: { fake: true },
    };
  }
}

function createInput(overrides: Partial<AgentRunInput> = {}): AgentRunInput {
  return {
    conversationId: "11111111-1111-4111-8111-111111111111",
    runId: "22222222-2222-4222-8222-222222222222",
    goal: "帮我找到修改密码",
    messageType: "speech_text",
    messageText: "帮我找到修改密码",
    currentSnapshot: {
      packageName: "com.example.target",
      activityName: "TargetActivity",
      screenWidth: 1080,
      screenHeight: 2400,
      imageWidth: 1080,
      imageHeight: 2400,
      nodes: [
        {
          text: "设置",
          contentDescription: "",
          className: "android.widget.TextView",
          clickable: true,
          editable: false,
          enabled: true,
          bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
        },
      ],
    },
    recentMessages: [],
    screenshot: {
      buffer: Buffer.from("fake-jpeg-binary"),
      mimeType: "image/jpeg",
    },
    ...overrides,
  };
}

describe("OpenAiCompatibleAgentRunner", () => {
  it("returns parsed tap output from a model client", async () => {
    const modelClient = new FakeModelClient(
      JSON.stringify({
        answer: "请点击设置",
        action: { type: "tap" },
        target: {
          label: "设置",
          bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
        },
        shouldContinue: true,
      }),
    );
    const runner = new OpenAiCompatibleAgentRunner({
      modelClient,
      model: "test-model",
    });

    const result = await runner.run(createInput());

    expect(result).toEqual({
      answer: "请点击设置",
      action: { type: "tap" },
      target: {
        label: "设置",
        bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
      },
      shouldContinue: true,
    });
    expect(modelClient.lastInput?.model).toBe("test-model");
    expect(Array.isArray(modelClient.lastInput?.messages[1]?.content)).toBe(
      true,
    );
  });

  it("can disable screenshot input for text-only models", async () => {
    const modelClient = new FakeModelClient(
      JSON.stringify({
        answer: "请点击设置",
        action: { type: "tap" },
        target: {
          label: "设置",
          bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
        },
        shouldContinue: true,
      }),
    );
    const runner = new OpenAiCompatibleAgentRunner({
      modelClient,
      model: "test-model",
      includeScreenshot: false,
    });

    await runner.run(createInput());

    expect(typeof modelClient.lastInput?.messages[1]?.content).toBe("string");
  });

  it("returns completed output when the model says the goal is done", async () => {
    const modelClient = new FakeModelClient(
      JSON.stringify({
        answer: "已经进入修改密码页面。",
        action: { type: "none" },
        target: null,
        shouldContinue: false,
      }),
    );
    const runner = new OpenAiCompatibleAgentRunner({
      modelClient,
      model: "test-model",
    });

    const result = await runner.run(
      createInput({
        messageType: "observed_click",
        messageText: null,
      }),
    );

    expect(result).toEqual({
      answer: "已经进入修改密码页面。",
      action: { type: "none" },
      target: null,
      shouldContinue: false,
    });
  });

  it("falls back when the model returns invalid content", async () => {
    const runner = new OpenAiCompatibleAgentRunner({
      modelClient: new FakeModelClient("not json"),
      model: "test-model",
    });

    const result = await runner.run(createInput());

    expect(result).toEqual(fallbackAgentRunOutput);
  });

  it("falls back for an unreliable target", async () => {
    const runner = new OpenAiCompatibleAgentRunner({
      modelClient: new FakeModelClient(
        JSON.stringify({
          answer: "请点击设置",
          action: { type: "tap" },
          target: null,
          shouldContinue: true,
        }),
      ),
      model: "test-model",
    });

    const result = await runner.run(createInput());

    expect(result).toEqual(fallbackAgentRunOutput);
  });
});
