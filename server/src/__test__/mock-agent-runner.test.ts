import { describe, expect, it } from "vitest";

import type { AgentRunInput } from "../agents/agent-runner.js";
import { mockAgentRunner } from "../agents/mock-agent-runner.js";

function createInput(overrides: Partial<AgentRunInput> = {}): AgentRunInput {
  return {
    conversationId: "11111111-1111-4111-8111-111111111111",
    runId: "22222222-2222-4222-8222-222222222222",
    goal: "帮我找到订单入口",
    messageType: "speech_text",
    messageText: "帮我找到订单入口",
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
    ...overrides,
  };
}

describe("mockAgentRunner", () => {
  it("suggests tapping 我的 on first speech when node exists", async () => {
    const result = await mockAgentRunner.run(
      createInput({
        currentSnapshot: {
          packageName: "com.example.target",
          activityName: "TargetActivity",
          screenWidth: 1080,
          screenHeight: 2400,
          imageWidth: 1080,
          imageHeight: 2400,
          nodes: [
            {
              text: "我的",
              contentDescription: "",
              className: "android.widget.TextView",
              clickable: true,
              editable: false,
              enabled: true,
              bounds: { left: 0, top: 2200, right: 200, bottom: 2390 },
            },
          ],
        },
      }),
    );

    expect(result).toEqual({
      answer: "请先点击底部的我的",
      action: { type: "tap" },
      target: {
        label: "我的",
        bounds: { left: 0, top: 2200, right: 200, bottom: 2390 },
      },
      shouldContinue: true,
    });
  });

  it("suggests tapping 订单 after observed click when node exists", async () => {
    const result = await mockAgentRunner.run(
      createInput({
        messageType: "observed_click",
        messageText: null,
        currentSnapshot: {
          packageName: "com.example.target",
          activityName: "TargetActivity",
          screenWidth: 1080,
          screenHeight: 2400,
          imageWidth: 1080,
          imageHeight: 2400,
          nodes: [
            {
              text: "订单",
              contentDescription: "",
              className: "android.widget.TextView",
              clickable: true,
              editable: false,
              enabled: true,
              bounds: { left: 300, top: 800, right: 780, bottom: 940 },
            },
          ],
        },
      }),
    );

    expect(result).toEqual({
      answer: "现在点击订单",
      action: { type: "tap" },
      target: {
        label: "订单",
        bounds: { left: 300, top: 800, right: 780, bottom: 940 },
      },
      shouldContinue: true,
    });
  });

  it("falls back to placeholder guidance when no node matches", async () => {
    const result = await mockAgentRunner.run(createInput());

    expect(result).toEqual({
      answer: "Analyze pipeline placeholder response.",
      action: { type: "none" },
      target: null,
      shouldContinue: true,
    });
  });
});
