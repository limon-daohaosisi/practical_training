import { describe, expect, it } from "vitest";

import {
  OpenAiCompatibleClient,
  resolveChatCompletionsEndpoint,
} from "../plugins/openai-compatible-client.js";

describe("resolveChatCompletionsEndpoint", () => {
  it("appends chat completions path to a v1 base URL", () => {
    expect(
      resolveChatCompletionsEndpoint("https://api-ai.vivo.com.cn/v1"),
    ).toBe("https://api-ai.vivo.com.cn/v1/chat/completions");
  });

  it("keeps a full chat completions URL unchanged", () => {
    expect(
      resolveChatCompletionsEndpoint(
        "https://api-ai.vivo.com.cn/v1/chat/completions",
      ),
    ).toBe("https://api-ai.vivo.com.cn/v1/chat/completions");
  });

  it("sends request_id query and non-stream JSON body", async () => {
    let capturedUrl = "";
    let capturedBody: unknown = null;
    const fetchImpl: typeof fetch = async (input, init) => {
      capturedUrl = input.toString();
      capturedBody = JSON.parse(init?.body?.toString() ?? "{}") as unknown;
      return new Response(
        JSON.stringify({
          choices: [{ message: { content: '{"answer":"ok"}' } }],
        }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      );
    };
    const client = new OpenAiCompatibleClient({
      apiKey: "app-key",
      baseUrl: "https://api-ai.vivo.com.cn/v1",
      fetchImpl,
      requestIdFactory: () => "11111111-1111-4111-8111-111111111111",
    });

    await client.createChatCompletion({
      model: "Volc-DeepSeek-V3.2",
      messages: [{ role: "user", content: "hello" }],
      temperature: 0,
    });

    expect(capturedUrl).toBe(
      "https://api-ai.vivo.com.cn/v1/chat/completions?request_id=11111111-1111-4111-8111-111111111111",
    );
    expect(capturedBody).toEqual({
      model: "Volc-DeepSeek-V3.2",
      messages: [{ role: "user", content: "hello" }],
      temperature: 0,
      stream: false,
    });
  });
});
