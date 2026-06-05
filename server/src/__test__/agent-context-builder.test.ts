import { describe, expect, it } from "vitest";

import type { AgentRunInput } from "../agents/agent-runner.js";
import { buildAgentChatMessages } from "../services/agent-context-builder.js";

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

describe("buildAgentChatMessages", () => {
  it("builds text-only first speech context when screenshot is disabled", () => {
    const messages = buildAgentChatMessages(createInput(), {
      includeScreenshot: false,
    });
    const userContext = JSON.parse(getUserText(messages)) as {
      task: { goal: string; contextFocus: string };
      nodes: Array<{ text: string }>;
    };

    expect(messages[0]?.content).toContain("只输出一个 JSON object");
    expect(userContext.task.goal).toBe("帮我找到修改密码");
    expect(userContext.task.contextFocus).toContain("首轮请求");
    expect(userContext.nodes).toEqual([
      expect.objectContaining({ text: "设置" }),
    ]);
    expect(typeof messages[1]?.content).toBe("string");
  });

  it("includes screenshot as an image_url content part by default", () => {
    const messages = buildAgentChatMessages(createInput());

    expect(getUserImageUrl(messages)).toBe(
      "data:image/jpeg;base64,ZmFrZS1qcGVnLWJpbmFyeQ==",
    );
  });

  it("can explicitly include screenshot as an image_url content part", () => {
    const messages = buildAgentChatMessages(createInput(), {
      includeScreenshot: true,
    });

    expect(getUserImageUrl(messages)).toBe(
      "data:image/jpeg;base64,ZmFrZS1qcGVnLWJpbmFyeQ==",
    );
  });

  it("builds click follow-up context with recent guidance", () => {
    const messages = buildAgentChatMessages(
      createInput({
        messageType: "observed_click",
        messageText: null,
        recentMessages: [
          {
            conversationId: "11111111-1111-4111-8111-111111111111",
            conversationStatus: "waiting_interaction",
            closedReason: null,
            createdAt: "2026-05-20T00:00:00.000Z",
            role: "assistant",
            messageType: "guidance",
            text: "请点击设置",
            guidance: {
              actionType: "tap",
              targetLabel: "设置",
              targetBounds: { left: 900, top: 80, right: 1040, bottom: 220 },
            },
          },
        ],
      }),
      { includeScreenshot: false },
    );
    const userContext = JSON.parse(getUserText(messages)) as {
      task: { messageType: string; contextFocus: string };
      recentMessages: Array<{ guidance: { targetLabel: string } | null }>;
    };

    expect(userContext.task.messageType).toBe("observed_click");
    expect(userContext.task.contextFocus).toContain("点击续跑");
    expect(userContext.recentMessages[0]?.guidance?.targetLabel).toBe("设置");
  });

  it("builds context when recentMessages is empty", () => {
    const messages = buildAgentChatMessages(
      createInput({ recentMessages: [] }),
      { includeScreenshot: false },
    );
    const userContext = JSON.parse(getUserText(messages)) as {
      recentMessages: unknown[];
    };

    expect(userContext.recentMessages).toEqual([]);
  });
});

function getUserContentParts(
  messages: ReturnType<typeof buildAgentChatMessages>,
) {
  const content = messages[1]?.content;
  if (!Array.isArray(content)) {
    throw new Error("Expected user content to include text and image parts.");
  }
  return content;
}

function getUserText(messages: ReturnType<typeof buildAgentChatMessages>) {
  const content = messages[1]?.content;
  if (typeof content === "string") {
    return content;
  }

  const textPart = getUserContentParts(messages)[0];
  if (textPart?.type !== "text") {
    throw new Error("Expected first user content part to be text.");
  }
  return textPart.text;
}

function getUserImageUrl(messages: ReturnType<typeof buildAgentChatMessages>) {
  const imagePart = getUserContentParts(messages)[1];
  if (imagePart?.type !== "image_url") {
    throw new Error("Expected second user content part to be image_url.");
  }
  return imagePart.image_url.url;
}
