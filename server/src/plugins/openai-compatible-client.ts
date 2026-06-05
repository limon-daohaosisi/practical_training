import { randomUUID } from "node:crypto";

import type {
  CreateChatCompletionInput,
  CreateChatCompletionOutput,
  ModelClient,
} from "../agents/model-client.js";

type OpenAiCompatibleClientOptions = {
  apiKey: string;
  baseUrl: string;
  fetchImpl?: typeof fetch;
  requestIdFactory?: () => string;
};

type ChatCompletionResponse = {
  error?: {
    code?: string;
    message?: string;
  };
  choices?: Array<{
    message?: {
      content?: string | null;
    };
  }>;
};

export function createOpenAiCompatibleClientFromEnv(
  env: NodeJS.ProcessEnv = process.env,
): OpenAiCompatibleClient {
  const apiKey = env.OPENAI_API_KEY;
  const baseUrl = env.OPENAI_BASE_URL;

  if (!apiKey) {
    throw new Error("OPENAI_API_KEY is required for OpenAI compatible client.");
  }
  if (!baseUrl) {
    throw new Error(
      "OPENAI_BASE_URL is required for OpenAI compatible client.",
    );
  }

  return new OpenAiCompatibleClient({ apiKey, baseUrl });
}

export class OpenAiCompatibleClient implements ModelClient {
  private readonly apiKey: string;
  private readonly endpoint: string;
  private readonly fetchImpl: typeof fetch;
  private readonly requestIdFactory: () => string;

  constructor(options: OpenAiCompatibleClientOptions) {
    this.apiKey = options.apiKey;
    this.endpoint = resolveChatCompletionsEndpoint(options.baseUrl);
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.requestIdFactory = options.requestIdFactory ?? randomUUID;
  }

  async createChatCompletion(
    input: CreateChatCompletionInput,
  ): Promise<CreateChatCompletionOutput> {
    const url = new URL(this.endpoint);
    url.searchParams.set("request_id", this.requestIdFactory());

    const response = await this.fetchImpl(url.toString(), {
      method: "POST",
      headers: {
        authorization: `Bearer ${this.apiKey}`,
        "content-type": "application/json; charset=utf-8",
      },
      body: JSON.stringify({
        model: input.model,
        messages: input.messages,
        temperature: input.temperature ?? 0,
        stream: false,
      }),
    });

    const raw = (await response.json().catch(() => null)) as unknown;
    if (!response.ok) {
      throw new Error(
        `OpenAI compatible chat completion failed with status ${response.status}.`,
      );
    }

    const parsed = raw as ChatCompletionResponse;
    if (parsed.error) {
      throw new Error(
        `OpenAI compatible chat completion failed: ${parsed.error.message ?? parsed.error.code ?? "unknown error"}.`,
      );
    }

    const content = parsed.choices?.[0]?.message?.content;
    if (!content) {
      throw new Error("OpenAI compatible response did not include content.");
    }

    return { content, raw };
  }
}

export function resolveChatCompletionsEndpoint(baseUrl: string): string {
  const trimmed = baseUrl.replace(/\/+$/, "");
  if (trimmed.endsWith("/chat/completions")) {
    return trimmed;
  }
  return `${trimmed}/chat/completions`;
}
