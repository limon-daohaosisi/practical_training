import { z } from "zod";

import type { AgentRunInput, AgentRunOutput } from "../agents/agent-runner.js";

export const fallbackAgentRunOutput: AgentRunOutput = {
  answer: "我暂时无法可靠定位下一步操作，请返回上一页或换个说法再试。",
  action: { type: "none" },
  target: null,
  shouldContinue: true,
};

const rawBoundsSchema = z.object({
  left: z.number().int(),
  top: z.number().int(),
  right: z.number().int(),
  bottom: z.number().int(),
});

const rawModelOutputSchema = z.object({
  answer: z.string().min(1),
  action: z.object({
    type: z.enum(["tap", "scroll", "none"]),
  }),
  target: z
    .object({
      label: z.string().min(1),
      bounds: rawBoundsSchema,
    })
    .nullable(),
  shouldContinue: z.boolean(),
});

export function parseAgentRunOutput(
  rawContent: string,
  input: AgentRunInput,
): AgentRunOutput {
  const jsonText = extractJsonObject(rawContent);
  if (!jsonText) {
    return fallbackAgentRunOutput;
  }

  const parsedJson = safeJsonParse(jsonText);
  if (parsedJson === null) {
    return fallbackAgentRunOutput;
  }

  const parsed = rawModelOutputSchema.safeParse(parsedJson);
  if (!parsed.success) {
    return fallbackAgentRunOutput;
  }

  const output = parsed.data;
  if (output.action.type === "tap") {
    if (!output.target || !isBoundsWithinScreen(output.target.bounds, input)) {
      return fallbackAgentRunOutput;
    }

    return output;
  }

  return {
    answer: output.answer,
    action: output.action,
    target: null,
    shouldContinue: output.shouldContinue,
  };
}

function extractJsonObject(content: string): string | null {
  const trimmed = content.trim();
  if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
    return trimmed;
  }

  const fencedMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  if (fencedMatch?.[1]) {
    return extractJsonObject(fencedMatch[1]);
  }

  const firstBrace = trimmed.indexOf("{");
  const lastBrace = trimmed.lastIndexOf("}");
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    return trimmed.slice(firstBrace, lastBrace + 1);
  }

  return null;
}

function safeJsonParse(jsonText: string): unknown | null {
  try {
    return JSON.parse(jsonText) as unknown;
  } catch {
    return null;
  }
}

function isBoundsWithinScreen(
  bounds: { left: number; top: number; right: number; bottom: number },
  input: AgentRunInput,
): boolean {
  return (
    bounds.left >= 0 &&
    bounds.top >= 0 &&
    bounds.right <= input.currentSnapshot.screenWidth &&
    bounds.bottom <= input.currentSnapshot.screenHeight &&
    bounds.left < bounds.right &&
    bounds.top < bounds.bottom
  );
}
