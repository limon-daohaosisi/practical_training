import type { ChatCompletionMessage } from "../agents/model-client.js";
import type { AgentRunInput } from "../agents/agent-runner.js";

const MAX_NODE_COUNT = 80;
const MAX_RECENT_MESSAGE_COUNT = 12;

type BuildAgentChatMessagesOptions = {
  includeScreenshot?: boolean;
};

export function buildAgentChatMessages(
  input: AgentRunInput,
  options: BuildAgentChatMessagesOptions = {},
): ChatCompletionMessage[] {
  return [
    {
      role: "system",
      content: buildSystemPrompt(),
    },
    {
      role: "user",
      content: buildUserMessageContent(input, options),
    },
  ];
}

function buildUserMessageContent(
  input: AgentRunInput,
  options: BuildAgentChatMessagesOptions,
): ChatCompletionMessage["content"] {
  const text = JSON.stringify(buildUserContext(input), null, 2);
  if (options.includeScreenshot === false) {
    return text;
  }

  return [
    {
      type: "text",
      text,
    },
    {
      type: "image_url",
      image_url: {
        url: buildScreenshotDataUrl(input),
      },
    },
  ];
}

function buildSystemPrompt(): string {
  return [
    "你是 Android 跨 App 辅助引导系统的页面分析器。",
    "你的任务是根据用户持续目标、当前事件、当前页面节点和最近指导历史，给出下一步可执行指导。",
    "只输出一个 JSON object，不要输出 markdown、解释性前后缀或代码块。",
    'JSON schema: {"answer": string, "action": {"type": "tap" | "scroll" | "none"}, "target": {"label": string, "bounds": {"left": number, "top": number, "right": number, "bottom": number}} | null, "shouldContinue": boolean}',
    "如果下一步需要点击，action.type 必须是 tap，target 必须来自当前页面节点或截图中可可靠定位的区域。",
    "如果需要用户滑动查找内容，action.type 使用 scroll，target 必须为 null。",
    "如果无法可靠定位目标，action.type 使用 none，target 必须为 null。",
    "如果用户目标已经完成，shouldContinue 必须为 false，action.type 使用 none，target 为 null。",
    "所有 bounds 必须使用屏幕坐标，而不是截图坐标。",
  ].join("\n");
}

function buildUserContext(input: AgentRunInput) {
  return {
    task: {
      goal: input.goal,
      messageType: input.messageType,
      messageText: input.messageText,
      contextFocus: getContextFocus(input.messageType),
    },
    screen: {
      packageName: input.currentSnapshot.packageName,
      activityName: input.currentSnapshot.activityName,
      screenWidth: input.currentSnapshot.screenWidth,
      screenHeight: input.currentSnapshot.screenHeight,
      imageWidth: input.currentSnapshot.imageWidth,
      imageHeight: input.currentSnapshot.imageHeight,
      screenshotMimeType: input.screenshot.mimeType,
      screenshotByteLength: input.screenshot.buffer.length,
    },
    recentMessages: summarizeRecentMessages(input),
    nodes: summarizeNodes(input),
  };
}

function buildScreenshotDataUrl(input: AgentRunInput): string {
  return `data:${input.screenshot.mimeType};base64,${input.screenshot.buffer.toString("base64")}`;
}

function getContextFocus(messageType: AgentRunInput["messageType"]): string {
  if (messageType === "speech_text") {
    return "首轮请求：理解用户目标，并基于当前页面给出第一步指导。";
  }
  if (messageType === "observed_click") {
    return "点击续跑：用户已经执行过上一步点击，请判断新页面并给出下一步。";
  }
  return "滑动续跑：用户已经滑动页面，请判断当前可见内容并给出下一步。";
}

function summarizeRecentMessages(input: AgentRunInput) {
  return input.recentMessages
    .slice(-MAX_RECENT_MESSAGE_COUNT)
    .map((message) => ({
      conversationId: message.conversationId,
      conversationStatus: message.conversationStatus,
      closedReason: message.closedReason,
      createdAt: message.createdAt,
      role: message.role,
      messageType: message.messageType,
      text: message.text,
      guidance: message.guidance,
    }));
}

function summarizeNodes(input: AgentRunInput) {
  return [...input.currentSnapshot.nodes]
    .sort((left, right) => scoreNode(right) - scoreNode(left))
    .slice(0, MAX_NODE_COUNT)
    .sort(
      (left, right) =>
        left.bounds.top - right.bounds.top ||
        left.bounds.left - right.bounds.left,
    )
    .map((node, index) => ({
      index,
      text: node.text,
      contentDescription: node.contentDescription,
      className: node.className,
      clickable: node.clickable,
      editable: node.editable,
      enabled: node.enabled,
      bounds: node.bounds,
    }));
}

function scoreNode(node: AgentRunInput["currentSnapshot"]["nodes"][number]) {
  let score = 0;
  if (node.text.trim()) score += 4;
  if (node.contentDescription.trim()) score += 4;
  if (node.clickable) score += 3;
  if (node.editable) score += 3;
  if (node.enabled) score += 1;
  return score;
}
