export type ChatCompletionContentPart =
  | {
      type: "text";
      text: string;
    }
  | {
      type: "image_url";
      image_url: {
        url: string;
      };
    };

export type ChatCompletionMessage = {
  role: "system" | "user" | "assistant";
  content: string | ChatCompletionContentPart[];
};

export type CreateChatCompletionInput = {
  model: string;
  messages: ChatCompletionMessage[];
  temperature?: number;
};

export type CreateChatCompletionOutput = {
  content: string;
  raw: unknown;
};

export interface ModelClient {
  createChatCompletion(
    input: CreateChatCompletionInput,
  ): Promise<CreateChatCompletionOutput>;
}
