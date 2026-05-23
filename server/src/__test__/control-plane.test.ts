import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type {
  AgentRunInput,
  AgentRunOutput,
  AgentRunner,
} from "../agents/agent-runner.js";
import { buildApp } from "../app.js";
import type { DbClient } from "../db/client.js";

/* ------------------------------------------------------------------ */
/*  Mock all DB modules so we can test control-plane routing           */
/*  without a real database.                                           */
/* ------------------------------------------------------------------ */

const {
  ConversationNotFoundError,
  InvalidConversationStateError,
} = vi.hoisted(() => {
  class ConversationNotFoundError extends Error {
    constructor(conversationId: string) {
      super(`Conversation ${conversationId} not found for this device`);
      this.name = "ConversationNotFoundError";
    }
  }

  class InvalidConversationStateError extends Error {
    constructor(conversationId: string, currentStatus: string) {
      super(
        `Conversation ${conversationId} is in status '${currentStatus}', expected 'waiting_interaction'`,
      );
      this.name = "InvalidConversationStateError";
    }
  }

  return { ConversationNotFoundError, InvalidConversationStateError };
});

const mockGetConversationGoal = vi.fn().mockResolvedValue("初始目标");
const mockGetRecentMessages = vi.fn().mockResolvedValue([]);
const mockIsDeviceBusy = vi.fn().mockResolvedValue(false);

const mockStartConversationFromSpeech = vi.fn();
const mockContinueConversationFromObservedEvent = vi.fn();
const mockCancelConversationByUserExit = vi.fn();
const mockCreateRun = vi.fn();
const mockCompleteRun = vi.fn();
const mockFailRun = vi.fn();
const mockSaveUserMessage = vi.fn();
const mockSaveAssistantGuidance = vi.fn();
const mockSaveScreenSnapshot = vi.fn();
const mockGetNextRunIndex = vi.fn().mockResolvedValue(1);
const mockGetLatestRunId = vi.fn().mockResolvedValue(null);

vi.mock("../db/queries/conversations.js", () => ({
  isDeviceBusy: (...args: unknown[]) => mockIsDeviceBusy(...args),
  getActiveConversationByDevice: vi.fn(),
  getConversationByIdAndDevice: vi.fn(),
}));

vi.mock("../db/queries/messages.js", () => ({
  getRecentMessagesByConversation: (...args: unknown[]) =>
    mockGetRecentMessages(...args),
  getConversationGoal: (...args: unknown[]) =>
    mockGetConversationGoal(...args),
}));

vi.mock("../db/transactions/conversations.js", () => ({
  ConversationNotFoundError,
  InvalidConversationStateError,
  startConversationFromSpeech: (...args: unknown[]) =>
    mockStartConversationFromSpeech(...args),
  continueConversationFromObservedEvent: (...args: unknown[]) =>
    mockContinueConversationFromObservedEvent(...args),
  cancelConversationByUserExit: (...args: unknown[]) =>
    mockCancelConversationByUserExit(...args),
}));

vi.mock("../db/transactions/runs.js", () => ({
  createRun: (...args: unknown[]) => mockCreateRun(...args),
  completeRun: (...args: unknown[]) => mockCompleteRun(...args),
  failRun: (...args: unknown[]) => mockFailRun(...args),
  getNextRunIndex: (...args: unknown[]) => mockGetNextRunIndex(...args),
  getLatestRunId: (...args: unknown[]) => mockGetLatestRunId(...args),
}));

vi.mock("../db/transactions/messages.js", () => ({
  saveUserMessage: (...args: unknown[]) => mockSaveUserMessage(...args),
  saveAssistantGuidance: (...args: unknown[]) =>
    mockSaveAssistantGuidance(...args),
}));

vi.mock("../db/transactions/snapshots.js", () => ({
  saveScreenSnapshot: (...args: unknown[]) => mockSaveScreenSnapshot(...args),
}));

/* ------------------------------------------------------------------ */
/*  Helpers                                                             */
/* ------------------------------------------------------------------ */

const MOCK_DB_CLIENT: DbClient = {
  pool: {} as never,
  db: {} as never,
};

const DEVICE_ID = "device-001";
const APP_PACKAGE = "com.example.target";
const CONVERSATION_ID = "11111111-1111-4111-8111-111111111111";
const RUN_ID = "22222222-2222-4222-8222-222222222222";

type TestAgentRunner = AgentRunner & { _capturedInputs: AgentRunInput[] };

function createTestAgentRunner(
  factory?: () => AgentRunOutput | Promise<AgentRunOutput>,
): TestAgentRunner {
  const capturedInputs: AgentRunInput[] = [];

  const run: AgentRunner["run"] = async (input) => {
    capturedInputs.push(input);
    if (factory) {
      return await factory();
    }
    return {
      answer: "mock answer",
      action: { type: "none" },
      target: null,
      shouldContinue: true,
    };
  };

  return { run, _capturedInputs: capturedInputs };
}

function buildBody(metadata: Record<string, unknown>): string {
  const boundary = "----test-boundary";
  const json = JSON.stringify(metadata);
  return [
    `--${boundary}`,
    `Content-Disposition: form-data; name="metadata"`,
    `Content-Type: application/json`,
    ``,
    json,
    `--${boundary}`,
    `Content-Disposition: form-data; name="screenshot"; filename="screen.jpg"`,
    `Content-Type: image/jpeg`,
    ``,
    `fake-jpeg-binary`,
    `--${boundary}--`,
  ].join("\r\n");
}

function newSpeechMetadata(overrides: Record<string, unknown> = {}) {
  return {
    deviceId: DEVICE_ID,
    messageType: "speech_text",
    messageText: "帮我找到修改密码",
    packageName: APP_PACKAGE,
    screenWidth: 1080,
    screenHeight: 2400,
    imageWidth: 1080,
    imageHeight: 2400,
    nodes: [],
    ...overrides,
  };
}

function newObservedMetadata(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    deviceId: DEVICE_ID,
    conversationId: CONVERSATION_ID,
    messageType: "observed_click",
    messageText: null,
    packageName: APP_PACKAGE,
    screenWidth: 1080,
    screenHeight: 2400,
    imageWidth: 1080,
    imageHeight: 2400,
    nodes: [],
    ...overrides,
  };
}

/* ------------------------------------------------------------------ */
/*  Tests                                                                */
/* ------------------------------------------------------------------ */

let app: ReturnType<typeof buildApp>;
let agent: TestAgentRunner;

beforeEach(() => {
  vi.clearAllMocks();

  mockIsDeviceBusy.mockResolvedValue(false);
  mockStartConversationFromSpeech.mockResolvedValue({
    id: CONVERSATION_ID,
    status: "analyzing",
    deviceId: DEVICE_ID,
    appPackageName: APP_PACKAGE,
  });
  mockContinueConversationFromObservedEvent.mockResolvedValue({
    id: CONVERSATION_ID,
    status: "analyzing",
    deviceId: DEVICE_ID,
    appPackageName: APP_PACKAGE,
  });
  mockCreateRun.mockResolvedValue({ id: RUN_ID });
  mockGetNextRunIndex.mockResolvedValue(1);
  mockGetLatestRunId.mockResolvedValue(null);
  mockGetConversationGoal.mockResolvedValue("帮我找到修改密码");
  mockGetRecentMessages.mockResolvedValue([]);
  mockSaveScreenSnapshot.mockResolvedValue(undefined);
  mockSaveUserMessage.mockResolvedValue(undefined);
  mockCompleteRun.mockResolvedValue(undefined);
  mockSaveAssistantGuidance.mockResolvedValue(undefined);
  mockCancelConversationByUserExit.mockResolvedValue(undefined);
  mockFailRun.mockResolvedValue(undefined);

  agent = createTestAgentRunner();
});

afterEach(async () => {
  if (app) {
    await app.close();
  }
});

describe("control-plane routing (A.1)", () => {
  it("routes speech_text to startConversationFromSpeech", async () => {
    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(response.statusCode).toBe(200);
    expect(mockStartConversationFromSpeech).toHaveBeenCalledWith(
      expect.anything(),
      DEVICE_ID,
      APP_PACKAGE,
    );
    expect(mockContinueConversationFromObservedEvent).not.toHaveBeenCalled();
  });

  it("routes observed_click to continueConversationFromObservedEvent", async () => {
    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newObservedMetadata()),
    });

    expect(response.statusCode).toBe(200);
    expect(mockContinueConversationFromObservedEvent).toHaveBeenCalledWith(
      expect.anything(),
      DEVICE_ID,
      CONVERSATION_ID,
    );
    expect(mockStartConversationFromSpeech).not.toHaveBeenCalled();
  });

  it("rejects observed_click without conversationId (400)", async () => {
    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const meta = newObservedMetadata();
    delete meta.conversationId;

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(meta),
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().code).toBe("INVALID_REQUEST");
  });
});

describe("device isolation (A.1/A.3)", () => {
  it("returns 404 when observed event targets wrong deviceId", async () => {
    mockContinueConversationFromObservedEvent.mockRejectedValue(
      new ConversationNotFoundError(CONVERSATION_ID),
    );

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(
        newObservedMetadata({ deviceId: "wrong-device" }),
      ),
    });

    expect(response.statusCode).toBe(404);
  });

  it("returns 409 when observed event on non-waiting_interaction conversation", async () => {
    mockContinueConversationFromObservedEvent.mockRejectedValue(
      new InvalidConversationStateError(CONVERSATION_ID, "completed"),
    );

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newObservedMetadata()),
    });

    expect(response.statusCode).toBe(409);
    expect(response.json().code).toBe("INVALID_STATE");
  });
});

describe("concurrency control (A.2)", () => {
  it("returns 409 DEVICE_BUSY when device already has an analyzing conversation", async () => {
    mockIsDeviceBusy.mockResolvedValue(true);

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(response.statusCode).toBe(409);
    expect(response.json().code).toBe("DEVICE_BUSY");
    expect(mockStartConversationFromSpeech).not.toHaveBeenCalled();
  });
});

describe("goal persistence (A.3)", () => {
  it("sets goal to messageText on first speech_text", async () => {
    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(agent._capturedInputs[0].goal).toBe("帮我找到修改密码");
  });

  it("loads persistent goal from DB on observed_click follow-up", async () => {
    mockGetConversationGoal.mockResolvedValue("帮我找到修改密码");

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newObservedMetadata()),
    });

    expect(agent._capturedInputs[0].goal).toBe("帮我找到修改密码");
    expect(mockGetConversationGoal).toHaveBeenCalledWith(
      expect.anything(),
      CONVERSATION_ID,
    );
  });
});

describe("result write-back (A.4)", () => {
  it("maps shouldContinue=true to waiting_interaction", async () => {
    agent = createTestAgentRunner(() => ({
      answer: "请点击我的",
      action: { type: "tap" },
      target: { label: "我的", bounds: { left: 0, top: 0, right: 100, bottom: 100 } },
      shouldContinue: true,
    }));

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(response.statusCode).toBe(200);
    expect(response.json().conversationStatus).toBe("waiting_interaction");
  });

  it("maps shouldContinue=false to completed", async () => {
    agent = createTestAgentRunner(() => ({
      answer: "操作完成",
      action: { type: "none" },
      target: null,
      shouldContinue: false,
    }));

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(response.statusCode).toBe(200);
    expect(response.json().conversationStatus).toBe("completed");
  });

  it("calls failRun and returns ANALYSIS_FAILED when agent throws", async () => {
    agent = createTestAgentRunner(() => {
      throw new Error("Model timeout");
    });

    app = buildApp({ agentRunner: agent, dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().code).toBe("ANALYSIS_FAILED");
    expect(mockFailRun).toHaveBeenCalledWith(
      expect.anything(),
      expect.any(String),
      CONVERSATION_ID,
      "ANALYSIS_FAILED",
      "Model timeout",
    );
  });
});

describe("cancel route (A.1)", () => {
  it("calls cancelConversationByUserExit with deviceId from body", async () => {
    app = buildApp({ dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: `/conversations/${CONVERSATION_ID}/cancel`,
      payload: { deviceId: DEVICE_ID },
    });

    expect(response.statusCode).toBe(200);
    expect(mockCancelConversationByUserExit).toHaveBeenCalledWith(
      expect.anything(),
      DEVICE_ID,
      CONVERSATION_ID,
    );
  });

  it("returns 404 when cancelling non-existent conversation", async () => {
    mockCancelConversationByUserExit.mockRejectedValue(
      new ConversationNotFoundError("nonexistent"),
    );

    app = buildApp({ dbClient: MOCK_DB_CLIENT });

    const response = await app.inject({
      method: "POST",
      url: "/conversations/nonexistent/cancel",
      payload: { deviceId: DEVICE_ID },
    });

    expect(response.statusCode).toBe(404);
  });
});

describe("full-loop scenario (A.5)", () => {
  it("completes a speech → observed → completed cycle", async () => {
    let conversationId: string;

    // Turn 1: speech_text
    const turn1ConvId = "aaaaaaaa-1111-4111-8111-111111111111";
    mockStartConversationFromSpeech.mockResolvedValue({
      id: turn1ConvId,
      status: "analyzing",
      deviceId: DEVICE_ID,
      appPackageName: APP_PACKAGE,
    });

    const agent1 = createTestAgentRunner(() => ({
      answer: "请先点击底部的我的",
      action: { type: "tap" as const },
      target: {
        label: "我的",
        bounds: { left: 0, top: 2200, right: 200, bottom: 2390 },
      },
      shouldContinue: true,
    }));

    app = buildApp({ agentRunner: agent1, dbClient: MOCK_DB_CLIENT });

    const res1 = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(newSpeechMetadata()),
    });

    expect(res1.statusCode).toBe(200);
    expect(res1.json().conversationStatus).toBe("waiting_interaction");
    conversationId = res1.json().conversationId;

    // Turn 2: observed_click
    await app.close();
    vi.clearAllMocks();
    mockIsDeviceBusy.mockResolvedValue(false);
    mockContinueConversationFromObservedEvent.mockResolvedValue({
      id: conversationId,
      status: "analyzing",
      deviceId: DEVICE_ID,
      appPackageName: APP_PACKAGE,
    });
    mockCreateRun.mockResolvedValue({ id: RUN_ID });
    mockGetNextRunIndex.mockResolvedValue(2);
    mockGetLatestRunId.mockResolvedValue("prev-run-id");
    mockGetConversationGoal.mockResolvedValue("帮我找到修改密码");
    mockGetRecentMessages.mockResolvedValue([]);
    mockSaveScreenSnapshot.mockResolvedValue(undefined);
    mockSaveUserMessage.mockResolvedValue(undefined);
    mockCompleteRun.mockResolvedValue(undefined);
    mockSaveAssistantGuidance.mockResolvedValue(undefined);

    const agent2 = createTestAgentRunner(() => ({
      answer: "修改密码功能已完成",
      action: { type: "none" as const },
      target: null,
      shouldContinue: false,
    }));

    app = buildApp({ agentRunner: agent2, dbClient: MOCK_DB_CLIENT });

    const res2 = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: { "content-type": "multipart/form-data; boundary=----test-boundary" },
      payload: buildBody(
        newObservedMetadata({ conversationId, messageType: "observed_click" }),
      ),
    });

    expect(res2.statusCode).toBe(200);
    expect(res2.json().conversationStatus).toBe("completed");
    expect(res2.json().conversationId).toBe(conversationId);
  });
});
