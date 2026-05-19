# Server Agent Loop 分工文档

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

## 子任务 A.1：会话事务与状态机落地

### 目标

把 RFC 中的会话创建、续接、替换和取消规则收敛到事务层。

### 输出

- `startConversationFromSpeech`
- `continueConversationFromObservedEvent`
- `cancelConversationByUserExit`
- 活跃会话、包名一致性、超时、并发保护逻辑
- `deviceId` 维度的会话隔离逻辑

### 验收

- 新 `speech_text` 可以替换旧活跃会话
- `observed_click` / `observed_scroll` 只能续接 `waiting_interaction`
- 非法续接不会偷偷新建会话
- 两次近同时事件不会把 `conversation.version` 覆盖乱
- 不能跨 `deviceId` 续接或取消别的设备的 `conversation`
- 任意查询当前活跃会话时，都必须同时带上 `deviceId`

---

## 子任务 A.2：单设备并发控制与运行互斥

### 目标

保证同一设备在任意时刻最多只有一个活跃 `run` 在执行，避免并发 run 覆盖状态和串会话。

### 输出

- 基于 `deviceId` 的运行互斥规则
- `conversation.status = analyzing` 时的拒绝或串行化策略
- 与 `conversation.version` 配合的并发保护逻辑
- 对并发请求的明确错误响应或幂等处理策略

### 验收

- 同一设备两次几乎同时到达的 `/analyze` 不会产生两个并行运行中的 `run`
- 一个设备最多只有一个 `run` 处于进行中
- 即使不同 `conversationId` 竞争，也不能绕过设备级互斥
- 并发冲突时，行为是可预期且可测试的

---

## 子任务 A.3：AgentRunInput 组装与上下文加载

### 目标

把当前请求、最近消息和截图组装成 `AgentRunInput`，供 `AgentRunner` 使用。

### 输出

- 当前请求到内部输入的映射逻辑
- `recentMessages` 查询与映射逻辑
- 当前页面快照 DTO
- `screenshot` buffer 透传逻辑
- 按 `deviceId + conversationId` 的安全加载逻辑

### 验收

- `recentMessages` 固定只取当前 `conversation`
- `recentMessages` 顺序固定为时间正序
- `goal` 在首轮等于 `speech_text`，续跑时保持不变
- assistant `guidance` 消息能同时带出 `text + guidance.actionType + guidance.target*`
- 截图不入正式数据库，但能稳定传给 `AgentRunner`
- `conversationId` 存在时，必须验证它属于当前 `deviceId`

---

## 子任务 A.4：结果回写与 HTTP 响应

### 目标

接收 `AgentRunner` 输出，完成落库和响应返回。

### 输出

- `runs` 的结果字段回写
- `assistant guidance message` 写入
- `conversation.status / wait_started_at / updated_at / version` 更新
- 对 Android 的成功响应
- 与 `deviceId` 绑定的安全返回逻辑

### 验收

- `MockAgentRunner` 返回后，数据库里能看到完整 `run` 和 assistant message
- `completed / failed / waiting_interaction` 三类状态都能正确落库
  - agent 正常返回 && shouldContinue = true
    -> conversationStatus = waiting_interaction
  - agent 正常返回 && shouldContinue = false
    -> conversationStatus = completed
  - agent 抛错 / parser 失败 / server 异常
    -> conversationStatus = failed

- API 返回结构和外部合同一致
- 不会因为错误的 `conversationId` 把别的设备会话状态更新掉

---

## 子任务 A.5：控制面测试与联调样本

### 目标

为 A 的控制面提供可回归的样本和测试。

### 输出

- 路由测试
- 事务测试
- 至少一条 RFC 示例链路测试
- 设备隔离测试
- 并发 run 测试

### 验收

- 能覆盖：
  - `speech_text -> guidance`
  - `observed_click -> next guidance`
  - `cancel`
  - `new speech replaces old conversation`
- 能覆盖：
  - 错误 `deviceId` 无法拿到或取消别人的 `conversation`
  - 同一设备同时发两次 `/analyze` 不会产生两个并发 `run`
- 失败路径能返回明确错误码

