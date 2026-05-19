# Server Agent Loop  分工文档

## 内部契约：AgentRunner

  这次 server 的目标，是把单轮 analyze 升级成一个事件驱动的多轮 loop。用户第一次用 speech_text 发起目标，比如“帮我找到修改密码”，server 创建一条
  conversation，之后每次用户点击或滑动，Android 再把新的页面快照用 observed_click / observed_scroll 发回来，server 不做长驻 loop，只做“一次事件，一次短
  run”，返回下一步指导。数据库是真源，核心表是 conversations / runs / messages / screen_snapshots，截图不入正式库，只在当前一次 run 内存里传给 agent。

  内部职责已经定了：A 做控制面，B 做推理面。A 负责外部接口、请求校验、会话创建/续接/取消、写库、并发和安全约束、调用 AgentRunner、回写结果、返回 HTTP 响应；
  B 负责真实 AgentRunner 的实现，也就是 prompt、上下文组装、模型调用、结构化解析。当前内部契约已经固定：AgentRunInput 里有 conversationId、runId、持续目标
  goal、当前事件类型 messageType、当前页面快照、当前 conversation 的 recentMessages、以及本次截图；AgentRunOutput 只返回 answer / action / target /
  shouldContinue。注意 conversationStatus 不由模型决定，而是 server 控制面根据 shouldContinue 和系统状态自己映射。

  MVP 还有几个硬约束：一次新的 speech_text 就视为开启一个新的完整 loop，不参与上一轮 loop 的上下文；recentMessages 只取当前 conversation 的消息，不跨
  conversation；goal 在首轮等于 speech_text，之后整轮续跑都持续传给 agent，避免模型忘记目标。所有会话查询、续接、取消都必须同时校验 deviceId，不能跨设备拿到
  别人的 conversation；同一设备任意时刻最多只能有一个 run 在执行，不能并发跑两条分析。现在合同和 mock 已经有了，A 可以先围绕 mock 跑通控制面闭环，B 按同一个
  接口补真实 agent。


### 目标

冻结 Server 控制面与推理面的最小内部接口，让 A 可以先用 mock 跑通 API 和写库闭环，B 可以并行实现真实 agent。

### AgentRunInput

```ts
type Bounds = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

type AgentActionType = "tap" | "scroll" | "none";

type AgentContextMessage = {
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
  guidance:
    | {
        actionType: AgentActionType;
        targetLabel: string | null;
        targetBounds: Bounds | null;
      }
    | null;
};

type AgentRunInput = {
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
    nodes: unknown;
  };
  recentMessages: AgentContextMessage[];
  screenshot: {
    buffer: Buffer;
    mimeType: string;
  };
};
```

### AgentRunOutput

```ts
type AgentRunOutput = {
  answer: string;
  action: {
    type: AgentActionType;
  };
  target:
    | {
        label: string;
        bounds: Bounds;
      }
    | null;
  shouldContinue: boolean;
};

interface AgentRunner {
  run(input: AgentRunInput): Promise<AgentRunOutput>;
}
```

### 契约说明

1. `recentMessages` 已合并“最近几步对话”和“最近几步 assistant 结构化指导”。
2. `guidance` 只在 `role=assistant` 且 `messageType=guidance` 时有值，其他情况固定为 `null`。
3. 不再单独传 `previousRunResult`，最近几步结构化指导统一从 `recentMessages` 中读取。
4. `screenshot` 不是数据库真源，不进正式表，只在当前一次短 `run` 内存中传给 `AgentRunner`。
5. `goal` 表示当前 loop 的持续目标；首轮等于本次 `speech_text`，续跑时沿用当前 `conversation` 的首轮目标。
6. MVP 下 `recentMessages` 只取当前 `conversation`，不跨 conversation 拼历史窗口。
7. `recentMessages` 按时间正序排列，并保留 `conversationId / conversationStatus / closedReason / createdAt`。
8. `conversationStatus` 不由模型决定，控制面根据 `shouldContinue` 和系统状态机自行映射。

---

## A 负责范围

### 目标

由 A 负责 Server 控制面，保证一条事件驱动链路可以稳定完成：

- 收请求
- 校验请求
- 创建或续接会话
- 写入 `conversation / run / message / screen_snapshot`
- 调用 `AgentRunner`
- 回写结果
- 返回 HTTP 响应

### A 拥有的目录

- `contracts/openapi/`
- `server/src/routes/`
- `server/src/db/queries/`
- `server/src/db/transactions/`
- `server/src/services/` 中与会话装配、错误映射、输入组装相关的部分
- `server/src/agents/agent-runner.ts`
- `server/src/agents/mock-agent-runner.ts`

### A 不负责

- Prompt 设计
- 模型调用实现
- 结构化结果解析策略
- OpenAI client 细节

---

## B 负责范围

### 目标

由 B 负责 Server 推理面，保证一次短 `run` 能基于当前页面和最近上下文产出稳定结构化指导。

### B 拥有的目录

- `server/src/agents/` 中真实 runner 实现
- `server/src/plugins/` 中模型 client 接线
- `server/src/services/` 中与 prompt、context builder、结果解析相关的部分

### B 不负责

- HTTP multipart 解析
- 会话事务与状态迁移
- DB 写入时机
- cancel 路由

---

## 子任务 B.1：真实 AgentRunner 设计与样本对齐

### 目标

基于冻结的 `AgentRunInput/Output`，明确真实 runner 的输入消费方式和结果格式。

### 输出

- `AgentRunner` 实现方案
- 一份基于样本输入的 agent 处理流程说明
- 对 `recentMessages` 与 `guidance` 字段的消费规则

### 验收

- B 不需要改 A 的事务设计也能开始实现
- 对输入哪些字段是必需的、哪些可忽略的有明确结论

---

## 子任务 B.2：Prompt 与上下文组装

### 目标

把当前页面、最近几步消息和结构化 guidance 组装成模型可消费的上下文。

### 输出

- Prompt 结构
- recent messages 摘要策略
- 当前节点与截图的组织方式
- 多轮续跑时的上下文拼装逻辑

### 验收

- `speech_text` 首轮和 `observed_click` / `observed_scroll` 续跑有不同上下文策略（也就是提示词按 messageType 采用不同上下文重点）
- 模型能同时看到最近自然语言和最近几步结构化 guidance
- 不依赖从 assistant 文本中反解 action/target

---

## 子任务 B.3：模型调用与结构化解析

### 目标

实现真实模型调用，并稳定解析出结构化结果。

### 输出

- 模型 client 接线
- 响应 schema
- 解析与校验逻辑
- 降级策略

### 验收

- 非法模型输出不会原样透传
- `tap / scroll / none` 与 `target` 关系可校验
- 无法可靠定位时可以稳定降级成 `action.type = none`

---

## 子任务 B.4：真实 Runner 实现

### 目标

把 prompt、模型调用和解析逻辑封装成可替换 `MockAgentRunner` 的真实实现。

### 输出

- `OpenAiAgentRunner` 或等价实现
- `run(input)` 的完整执行逻辑

### 验收

- A 只改装配就能切到真实 runner
- `run(input)` 返回值严格符合 `AgentRunOutput`
- 能对一份固定样本返回稳定结构化结果

---

## 子任务 B.5：推理面测试与回归样本

### 目标

让 B 的逻辑能独立于 route 和 DB 写路径回归验证。

### 输出

- `AgentRunner` 单测
- prompt/context builder 单测
- parser 单测
- 样本输入输出 fixture

### 验收

- 至少覆盖：
  - 首轮语音
  - 点击续跑
  - 无法定位目标
  - 已完成无需继续引导
- 模型返回结构变化时，测试能第一时间暴露问题



