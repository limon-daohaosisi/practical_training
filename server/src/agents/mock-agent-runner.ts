import type {
  AgentRunInput,
  AgentRunOutput,
  AgentRunner,
  Bounds,
} from "./agent-runner.js";

type MatchCandidate = {
  label: string;
  bounds: Bounds;
};

class MockAgentRunner implements AgentRunner {
  async run(input: AgentRunInput): Promise<AgentRunOutput> {
    if (input.messageType === "speech_text") {
      const myTab = findNodeByKeywords(input, ["我的"]);
      if (myTab) {
        return {
          answer: "请先点击底部的我的",
          action: { type: "tap" },
          target: myTab,
          shouldContinue: true,
        };
      }
    }

    if (input.messageType === "observed_click") {
      const orderEntry = findNodeByKeywords(input, ["订单"]);
      if (orderEntry) {
        return {
          answer: "现在点击订单",
          action: { type: "tap" },
          target: orderEntry,
          shouldContinue: true,
        };
      }
    }

    if (input.messageType === "observed_scroll") {
      return {
        answer: "已收到滑动，请继续查看当前页面。",
        action: { type: "none" },
        target: null,
        shouldContinue: true,
      };
    }

    return {
      answer: "Analyze pipeline placeholder response.",
      action: { type: "none" },
      target: null,
      shouldContinue: true,
    };
  }
}

function findNodeByKeywords(
  input: AgentRunInput,
  keywords: string[],
): MatchCandidate | null {
  for (const node of input.currentSnapshot.nodes) {
    const haystacks = [node.text, node.contentDescription]
      .map((value) => value.trim())
      .filter((value) => value.length > 0);

    for (const haystack of haystacks) {
      for (const keyword of keywords) {
        if (haystack.includes(keyword)) {
          return {
            label: keyword,
            bounds: node.bounds,
          };
        }
      }
    }
  }

  return null;
}

export const mockAgentRunner = new MockAgentRunner();
