import type {
  AgentRunInput,
  AgentRunOutput,
  AgentRunner,
} from "./agent-runner.js";
import type { ModelClient } from "./model-client.js";
import { buildAgentChatMessages } from "../services/agent-context-builder.js";
import {
  fallbackAgentRunOutput,
  parseAgentRunOutput,
} from "../services/agent-output-parser.js";

type OpenAiCompatibleAgentRunnerOptions = {
  modelClient: ModelClient;
  model: string;
  includeScreenshot?: boolean;
};

export function createOpenAiCompatibleAgentRunnerFromEnv(
  modelClient: ModelClient,
  env: NodeJS.ProcessEnv = process.env,
): OpenAiCompatibleAgentRunner {
  const model = env.OPENAI_MODEL;
  if (!model) {
    throw new Error("OPENAI_MODEL is required for OpenAI compatible runner.");
  }

  return new OpenAiCompatibleAgentRunner({
    modelClient,
    model,
    includeScreenshot: env.OPENAI_INCLUDE_SCREENSHOT === "true",
  });
}

export class OpenAiCompatibleAgentRunner implements AgentRunner {
  private readonly modelClient: ModelClient;
  private readonly model: string;
  private readonly includeScreenshot: boolean;

  constructor(options: OpenAiCompatibleAgentRunnerOptions) {
    this.modelClient = options.modelClient;
    this.model = options.model;
    this.includeScreenshot = options.includeScreenshot ?? false;
  }

  async run(input: AgentRunInput): Promise<AgentRunOutput> {
    try {
      const completion = await this.modelClient.createChatCompletion({
        model: this.model,
        messages: buildAgentChatMessages(input, {
          includeScreenshot: this.includeScreenshot,
        }),
        temperature: 0,
      });

      return parseAgentRunOutput(completion.content, input);
    } catch {
      return fallbackAgentRunOutput;
    }
  }
}
