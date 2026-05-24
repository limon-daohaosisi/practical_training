import { describe, expect, it } from "vitest";

import type { AgentRunInput } from "../agents/agent-runner.js";
import {
  fallbackAgentRunOutput,
  parseAgentRunOutput,
} from "../services/agent-output-parser.js";

function createInput(): AgentRunInput {
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
      nodes: [],
    },
    recentMessages: [],
    screenshot: {
      buffer: Buffer.from("fake-jpeg-binary"),
      mimeType: "image/jpeg",
    },
  };
}

describe("parseAgentRunOutput", () => {
  it("accepts a valid tap output", () => {
    const result = parseAgentRunOutput(
      JSON.stringify({
        answer: "请点击设置",
        action: { type: "tap" },
        target: {
          label: "设置",
          bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
        },
        shouldContinue: true,
      }),
      createInput(),
    );

    expect(result).toEqual({
      answer: "请点击设置",
      action: { type: "tap" },
      target: {
        label: "设置",
        bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
      },
      shouldContinue: true,
    });
  });

  it("falls back when tap output has no target", () => {
    const result = parseAgentRunOutput(
      JSON.stringify({
        answer: "请点击设置",
        action: { type: "tap" },
        target: null,
        shouldContinue: true,
      }),
      createInput(),
    );

    expect(result).toEqual(fallbackAgentRunOutput);
  });

  it("normalizes target to null for none output", () => {
    const result = parseAgentRunOutput(
      JSON.stringify({
        answer: "当前页面已经完成",
        action: { type: "none" },
        target: {
          label: "设置",
          bounds: { left: 900, top: 80, right: 1040, bottom: 220 },
        },
        shouldContinue: false,
      }),
      createInput(),
    );

    expect(result).toEqual({
      answer: "当前页面已经完成",
      action: { type: "none" },
      target: null,
      shouldContinue: false,
    });
  });

  it("falls back when bounds are outside the screen", () => {
    const result = parseAgentRunOutput(
      JSON.stringify({
        answer: "请点击设置",
        action: { type: "tap" },
        target: {
          label: "设置",
          bounds: { left: 900, top: 80, right: 1200, bottom: 220 },
        },
        shouldContinue: true,
      }),
      createInput(),
    );

    expect(result).toEqual(fallbackAgentRunOutput);
  });

  it("falls back for non JSON content", () => {
    const result = parseAgentRunOutput("请点击设置", createInput());

    expect(result).toEqual(fallbackAgentRunOutput);
  });
});
