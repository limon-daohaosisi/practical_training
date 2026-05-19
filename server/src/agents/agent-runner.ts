export type Bounds = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

export type AgentActionType = "tap" | "scroll" | "none";

export type AgentGuidanceContext = {
  actionType: AgentActionType;
  targetLabel: string | null;
  targetBounds: Bounds | null;
};

export type AgentContextMessage = {
  conversationId: string;
  conversationStatus:
    | "analyzing"
    | "waiting_interaction"
    | "completed"
    | "failed"
    | "cancelled"
    | "expired";
  closedReason: string | null;
  createdAt: string;
  role: "user" | "assistant";
  messageType:
    | "speech_text"
    | "observed_click"
    | "observed_scroll"
    | "guidance";
  text: string | null;
  guidance: AgentGuidanceContext | null;
};

export type AgentRunInput = {
  conversationId: string;
  runId: string;
  goal: string;
  messageType: "speech_text" | "observed_click" | "observed_scroll";
  messageText: string | null;
  currentSnapshot: {
    packageName: string;
    activityName: string | null;
    screenWidth: number;
    screenHeight: number;
    imageWidth: number;
    imageHeight: number;
    nodes: Array<{
      text: string;
      contentDescription: string;
      className: string;
      clickable: boolean;
      editable: boolean;
      enabled: boolean;
      bounds: Bounds;
    }>;
  };
  recentMessages: AgentContextMessage[];
  screenshot: {
    buffer: Buffer;
    mimeType: string;
  };
};

export type AgentRunOutput = {
  answer: string;
  action: {
    type: AgentActionType;
  };
  target: {
    label: string;
    bounds: Bounds;
  } | null;
  shouldContinue: boolean;
};

export interface AgentRunner {
  run(input: AgentRunInput): Promise<AgentRunOutput>;
}
