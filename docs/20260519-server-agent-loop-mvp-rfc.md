# Server Agent Loop MVP 数据库与状态机 RFC

## 1. 文档目标

本 RFC 用于确定 Server 端第一版多轮 agent 引导能力的最小实现边界，重点回答两个问题：

1. MVP 阶段数据库应该如何建模。
2. MVP 阶段 agent loop 应该如何驱动和落状态。

本文件的结论主要服务于以下工作：

1. `server/` 侧 agent 层与持久化层并行建设。
2. Android 端等待态、点击/滑动监听、续跑上传逻辑建设。
3. 后续 `contracts/openapi/` 多轮接口扩展时的内部实现依据。

## 2. 背景

当前仓库中的 `server` 仍处于单轮 `analyze` 骨架阶段。

项目的真实产品形态并不是一次性问答，而是：

1. 用户先说出目标。
2. Server 根据当前页面给出一步指导。
3. 用户执行点击或滑动。
4. Android 端观察到交互后重新采集页面。
5. Server 再根据新页面继续给出下一步指导。

在这个形态下，如果继续按单轮问答思路设计，会出现几个问题：

1. 用户点击后不一定会再次说话。
2. Android 端不适合承担复杂的页面语义判断。
3. Server 不适合维持一个长时间阻塞、等待客户端动作的内存 loop。
4. 仅有 `run / message / message_part` 不能清晰表达页面快照和会话推进关系。

因此，MVP 阶段应改为事件驱动的短运行模式。

## 3. 设计结论

本 RFC 的核心结论如下：

1. agent loop 采用事件驱动，而不是 Server 端长驻内存循环。
2. Android 端在 MVP 只监听两类续跑事件：`click` 与 `scroll`。
3. Android 端不负责判断“用户是否正确完成了上一步”，只负责观察到交互后重新采集页面并上传。
4. Server 端每次只执行一次短 `run`，执行完成后进入等待态。
5. MVP 阶段合并 `turn` 与 `run`，直接把 `run` 作为会话中的一次事件驱动分析步骤。
6. 页面快照是第一类业务数据，必须单独建模，不能隐含在 message 中。
7. MVP 阶段引入 `messages` 表，统一承载用户语音、点击、滑动和 assistant 回复。
8. MVP 阶段暂不引入 `message_part`，多模态消息分片留到后续再扩展。

## 4. MVP 范围

本 RFC 覆盖：

1. 会话、执行步骤、页面快照、执行记录的数据库结构。
2. Android 与 Server 协同的最小状态机。
3. 点击、滑动事件驱动的续跑机制。

本 RFC 不覆盖：

1. 中途语音打断和实时自由对话。
2. `message_part` 的正式落库方案。
3. 自动点击、自动填表等代理执行能力。
4. 长时间后台常驻监听策略。
5. WebSocket、流式输出或 Realtime API。

说明：

1. MVP 仍允许用户通过第一句语音发起任务。
2. MVP 的后续续跑只依赖 `click` / `scroll`，不依赖用户再次说话。
3. 中途更复杂的自由语音交互留到下一阶段单独设计。

## 5. 设计原则

本 RFC 采用以下原则：

1. 优先保证 Android -> Server -> 下一步指导 这条主链路可跑通。
2. 优先让 Android 侧逻辑保持机械和轻量，不做复杂语义推断。
3. 优先把页面快照作为核心上下文，而不是把历史对话堆成长 prompt。
4. 优先让会话状态存数据库，而不是依赖 Server 进程内存。
5. 只为当前 MVP 需要的能力建模，不为未来平台化一次性做全。

## 6. 交互模型

MVP 阶段统一采用以下交互链路：

1. 用户通过语音发起一次任务，例如“帮我找到退款入口”。
2. Android 端采集当前页面的 `nodes`、截图、包名、Activity 信息，并上传到 Server。
3. Server 创建会话并执行一次 `run`。
4. Server 返回一步指导，包括：
   - `answer`
   - `target`
   - `action`
5. Android 端进行高亮和 TTS 播报，然后进入等待态。
6. Android 端只监听 `click` 与 `scroll` 两类用户交互事件。
7. 监听到交互事件后，Android 端等待一个短稳定窗口，再重新采集当前页面并再次上传。
8. Server 根据最近消息、上一步输出和当前新页面继续执行下一次短 `run`。
9. 如此循环，直到会话完成、失败或被用户主动结束。

这个模型的重要特点是：

1. Server 不持续等待客户端动作。
2. 每次继续分析都由 Android 新事件触发。
3. Android 只负责上传事实，不负责判定语义是否成功。

## 7. 为什么 Android 端只做轻判断

MVP 阶段不建议 Android 端承担以下职责：

1. 判断用户是不是点对了目标。
2. 判断页面是不是已经进入预期流程。
3. 判断当前页面和模型意图是否匹配。

原因如下：

1. 第三方 App 页面差异极大，客户端本地规则很快失控。
2. 点击和滑动后的结果往往需要结合截图和节点一起看，Android 本地做语义推断成本高。
3. 这类语义判断更适合集中在 Server 侧演进。

因此 Android 端在 MVP 只做以下事情：

1. 监听 `click` / `scroll`。
2. 去抖动并等待页面短暂稳定。
3. 重新采集页面。
4. 把“发生了什么”和“现在页面是什么”传给 Server。

## 8. 核心实体与归属关系

MVP 阶段的核心实体如下：

1. `conversation`
2. `run`
3. `message`
4. `screen_snapshot`

推荐关系如下：

```text
conversations
  1 -> N runs
  1 -> N messages

runs
  N -> 1 conversations
  1 -> 1 screen_snapshots   (MVP 约束)
  N -> 1 previous_run       (自关联，可空)
  1 -> N messages
```

最关键的归属约束：

1. `conversation` 是会话主实体。
2. `run` 表示会话中的一次事件驱动分析步骤。
3. `message` 表示会话中的一次用户或 assistant 交互消息。
4. `screen_snapshot` 表示这次 `run` 所依赖的真实页面上下文。

结论：

1. `run` 直接归属于 `conversation`。
2. `messages` 通过 `run_id` 归属于具体一次 `run`。
3. 只有当一步内部需要多次自动重试、工具调用链或多次模型尝试时，再拆回更细的执行层。
4. 截图默认不进入正式数据库模型，只在单次 `run` 内存中使用。

## 9. 数据库设计

### 9.1 `conversations`

用途：

1. 表示一段连续的引导会话。
2. 保存当前会话的状态和推进位置。
3. 作为 Android 端多次续跑请求的统一归属对象。

建议字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | `uuid` | 主键 |
| `device_id` | `text` | Android 设备或安装实例标识 |
| `app_package_name` | `text` | 当前会话主要服务的第三方 App 包名 |
| `status` | `enum` | `analyzing / waiting_interaction / completed / failed / cancelled / expired` |
| `wait_started_at` | `timestamptz nullable` | 进入等待态时间 |
| `closed_reason` | `text nullable` | 会话关闭原因，例如 `completed`、`replaced_by_new_speech`、`idle_timeout` |
| `ended_at` | `timestamptz nullable` | 会话结束时间 |
| `created_at` | `timestamptz` | 创建时间 |
| `updated_at` | `timestamptz` | 更新时间 |
| `version` | `integer` | 乐观锁版本号，默认从 1 开始 |

说明：

1. `version` 用于避免同一会话被并发续跑覆盖。
2. `closed_reason` 仅在会话进入终态时写入，用于说明为什么结束或被替换。

索引建议：

1. `index conversations_device_id_status_idx (device_id, status)`
2. `index conversations_updated_at_idx (updated_at desc)`
3. 产品要求一个设备同时只允许一个活跃会话，增加部分唯一约束：`device_id + active status`

### 9.2 `runs`

用途：

1. 表示会话中的一次事件驱动分析步骤。
2. 承载这一步的模型执行和结构化输出。
3. 作为当前 MVP 的主执行表。

建议字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | `uuid` | 主键 |
| `conversation_id` | `uuid` | 归属会话 |
| `run_index` | `integer` | 会话内从 1 递增 |
| `previous_run_id` | `uuid nullable` | 上一步 run，自关联 |
| `status` | `enum` | `received / running / completed / failed` |
| `observed_at` | `timestamptz nullable` | 续跑触发事件发生时间 |
| `model_provider` | `text` | 例如 `openai` |
| `model_name` | `text` | 实际模型名 |
| `prompt_version` | `text nullable` | Prompt 版本标识 |
| `input_context_json` | `jsonb` | 真正送进模型前的结构化输入 |
| `response_raw_json` | `jsonb nullable` | 原始模型返回 |
| `result_answer_text` | `text nullable` | 结构化解析后的回答 |
| `result_action_type` | `enum nullable` | 结构化解析后的动作类型 |
| `result_target_label` | `text nullable` | 结构化解析后的目标描述 |
| `result_target_bounds_json` | `jsonb nullable` | 结构化解析后的目标区域 |
| `latency_ms` | `integer nullable` | 执行耗时 |
| `error_code` | `text nullable` | 失败码 |
| `error_message` | `text nullable` | 失败详情 |
| `started_at` | `timestamptz` | 执行开始时间 |
| `completed_at` | `timestamptz nullable` | 执行结束时间 |
| `created_at` | `timestamptz` | 创建时间 |

说明：

1. 当前 MVP 中，一次事件只生成一次 `run`。
2. `run` 不直接保存原始用户输入文本，输入内容统一来自关联的 `messages`。
3. 如果未来一轮内需要多次模型尝试，再把执行层从 `run` 中拆出去。

索引建议：

1. `unique index runs_conversation_id_run_index_uidx (conversation_id, run_index)`
2. `index runs_previous_run_id_idx (previous_run_id)`
3. `index runs_conversation_id_created_at_idx (conversation_id, created_at)`

### 9.3 `messages`

用途：

1. 统一保存用户和 assistant 的会话交互。
2. 统一表达用户语音、点击、滑动和 assistant 的指导回复。
3. 作为 `run` 的原始输入和自然语言输出来源。

建议字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | `uuid` | 主键 |
| `conversation_id` | `uuid` | 归属会话 |
| `run_id` | `uuid` | 归属执行步骤 |
| `role` | `enum` | `user / assistant` |
| `message_type` | `enum` | `speech_text / observed_click / observed_scroll / guidance` |
| `text` | `text nullable` | 文本内容；点击、滑动时可为空 |
| `created_at` | `timestamptz` | 创建时间 |

说明：

1. `speech_text` 表示用户语音转写后的文本消息。
2. `observed_click` 与 `observed_scroll` 表示用户交互事件，也视为一类 `user` message。
3. `guidance` 表示 assistant 返回给用户的自然语言指导。
4. 当前 MVP 不拆 `message_parts`，点击、滑动先只用 `message_type` 表达。
5. 当前 MVP 约束每个 `run` 最多一条 `user` message 和一条 `assistant` message，避免同一步内消息歧义。

索引建议：

1. `index messages_conversation_id_created_at_idx (conversation_id, created_at)`
2. `index messages_run_id_created_at_idx (run_id, created_at)`

### 9.4 `screen_snapshots`

用途：

1. 保存每一步输入所对应的真实页面上下文。
2. 让 Server 可以基于节点和尺寸信息恢复这一步的页面结构上下文。
3. 作为后续页面变化判断的基础事实。

建议字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | `uuid` | 主键 |
| `conversation_id` | `uuid` | 归属会话 |
| `run_id` | `uuid` | 归属执行步骤，MVP 中建议唯一 |
| `package_name` | `text` | 当前页面包名 |
| `activity_name` | `text nullable` | 当前 Activity 或窗口名 |
| `screen_width` | `integer` | 屏幕宽度 |
| `screen_height` | `integer` | 屏幕高度 |
| `image_width` | `integer` | 上传截图宽度 |
| `image_height` | `integer` | 上传截图高度 |
| `nodes_json` | `jsonb` | 原始标准化节点数组 |
| `node_count` | `integer` | 节点数量 |
| `captured_at` | `timestamptz` | 客户端采集时间 |
| `created_at` | `timestamptz` | 入库时间 |

说明：

1. `nodes_json` 在 MVP 直接存 JSON，不拆节点子表。
2. 截图默认不持久化存储，不在 `screen_snapshots` 中保存文件引用。
3. 这是为了先服务页面结构分析，不为图片回放和节点级检索提前过度建模。
4. 如果后续需要，可再增加派生摘要字段，但本 RFC 不强制。

索引建议：

1. `unique index screen_snapshots_run_id_uidx (run_id)`
2. `index screen_snapshots_conversation_id_created_at_idx (conversation_id, created_at)`

### 9.5 截图处理策略

MVP 阶段截图处理建议如下：

1. 请求进入后，截图读入内存，作为本次 `run` 的模型输入。
2. 本次 `run` 完成后，默认不将截图写入正式数据库。
3. 正式数据库只保留页面尺寸、节点和结构化分析结果。
4. 如需排查问题，可在 debug 模式下将截图临时落盘，但不纳入正式数据模型。

这样做的原因是：

1. 当前截图的主要作用是服务单次推理，而不是长期复用。
2. 默认不存图可以显著降低隐私、存储和运维成本。
3. 当前 MVP 的重点是先跑通会话推进，而不是建设回放素材库。

## 10. Conversation 生命周期规则

MVP 阶段必须明确 `conversation` 的创建、续接和关闭规则，避免客户端和服务端各自实现出不同逻辑。

### 创建规则

1. 只有收到一条 `speech_text` 类型的 `user` message 时，才允许创建新的 `conversation`。
2. 如果当前设备不存在活跃 `conversation`，收到 `speech_text` 后直接新建会话。
3. 如果当前设备已有活跃 `conversation`，再次收到新的 `speech_text` 时，先将旧会话标记为 `cancelled`，`closed_reason = replaced_by_new_speech`，再创建新会话。
4. 如果当前请求来自新的 `app_package_name`，且消息类型为 `speech_text`，也应直接创建新会话，而不是复用旧会话。

### 续接规则

1. `observed_click` 与 `observed_scroll` 不能单独创建新会话。
2. 这两类消息只能续接当前设备上唯一的活跃 `conversation`。
3. 续接时必须满足：
   - `conversation.status = waiting_interaction`
   - 当前 `package_name` 与 `conversation.app_package_name` 一致
   - 会话未超过等待超时窗口
4. 如果以上任一条件不满足，Server 不应自动新建会话，而应要求客户端重新通过 `speech_text` 发起新任务。

### 关闭规则

1. 当任务完成时，`conversation.status = completed`，`closed_reason = completed`。
2. 当执行出现不可恢复错误时，`conversation.status = failed`，`closed_reason = server_error`。
3. 当用户主动退出时，`conversation.status = cancelled`，`closed_reason = user_exit`。
4. 当用户在 loop 中点击中止键时，`conversation.status = cancelled`，`closed_reason = user_exit`。
5. 当新的 `speech_text` 替换掉旧会话时，`conversation.status = cancelled`，`closed_reason = replaced_by_new_speech`。
6. 当等待时间超过超时窗口时，`conversation.status = expired`，`closed_reason = idle_timeout`。

### 超时规则

1. MVP 默认等待超时窗口建议先定为 3 分钟。
2. 超时判断只针对 `waiting_interaction` 状态的会话。
3. 一旦超时，后续 `observed_click` / `observed_scroll` 不再允许续接该会话。

### 设备约束

1. 同一设备同一时刻最多只允许一个活跃 `conversation`。
2. 活跃会话默认绑定一个 `app_package_name`。
3. 来自其他包名的点击或滑动事件不能续接旧会话。
4. 当会话处于 loop 中时，Android 端的语音键可被替换为中止键；点击中止键后当前会话结束，下次语音重新新建会话。

## 11. 会话状态机

### 11.1 Server 侧会话状态

MVP 建议定义以下会话状态：

1. `analyzing`
2. `waiting_interaction`
3. `completed`
4. `failed`
5. `cancelled`
6. `expired`

状态含义：

1. `analyzing`
   Server 正在处理当前这一步，尚未返回下一步指导。
2. `waiting_interaction`
    Server 已返回一步指导，等待 Android 端观察到用户 `click` 或 `scroll` 后再续跑。
3. `completed`
   当前会话已完成，不再继续。
4. `failed`
   当前会话因执行错误或不可恢复问题结束。
5. `cancelled`
   用户主动中止或客户端主动结束。
6. `expired`
    长时间无后续事件，会话过期。

补充约束：

1. 只有 `speech_text` 能启动新会话。
2. `observed_click` 与 `observed_scroll` 只能续接 `waiting_interaction` 状态的会话。
3. 新的 `speech_text` 默认替换旧会话，而不是插入到旧会话中。

状态转换建议：

```text
start
  -> analyzing

analyzing
  -> waiting_interaction   当 run 成功且给出下一步动作
  -> completed             当 run 成功但无需继续引导
  -> failed                当 run 执行失败

waiting_interaction
  -> analyzing             当收到 observed_click 或 observed_scroll 新 run
  -> cancelled             当用户点击中止键或客户端结束会话
  -> expired               当超出会话存活时间
```

### 11.2 Android 侧运行状态

MVP 建议定义以下客户端运行状态：

1. `idle`
2. `capturing_initial_snapshot`
3. `awaiting_server_response`
4. `rendering_guidance`
5. `waiting_click_or_scroll`
6. `capturing_followup_snapshot`
7. `stopped`

状态含义：

1. `idle`
   当前没有活跃辅助会话。
2. `capturing_initial_snapshot`
   用户说完第一句后，客户端开始采集首轮页面信息。
3. `awaiting_server_response`
   客户端已上传本次数据，等待 Server 返回指导结果。
4. `rendering_guidance`
   客户端正在高亮并 TTS 播报本次指导。
5. `waiting_click_or_scroll`
   客户端等待用户执行 `click` 或 `scroll`。
6. `capturing_followup_snapshot`
   已观察到交互事件，等待短稳定窗口后重新采集页面。
7. `stopped`
   会话结束、用户中止或异常终止。

状态转换建议：

```text
idle
  -> capturing_initial_snapshot
  -> awaiting_server_response
  -> rendering_guidance
  -> waiting_click_or_scroll

waiting_click_or_scroll
  -> capturing_followup_snapshot   当观察到 click 或 scroll
  -> stopped                       当用户点击中止键或超时退出

capturing_followup_snapshot
  -> awaiting_server_response

awaiting_server_response
  -> rendering_guidance            当 Server 返回新一步指导
  -> stopped                       当 Server 表示 completed / failed
```

## 12. 交互消息模型

MVP 阶段统一定义四类消息类型：

1. `speech_text`
2. `observed_click`
3. `observed_scroll`
4. `guidance`

含义：

1. `speech_text`
   用户语音转写后的文本消息。
2. `observed_click`
   Android 端在等待态观察到用户点击行为后，创建的一条 `user` message。
3. `observed_scroll`
   Android 端在等待态观察到用户滑动行为后，创建的一条 `user` message。
4. `guidance`
   assistant 返回给用户的自然语言指导消息。

MVP 约束：

1. 不引入 `user_interrupt` 的单独消息类型；用户中止作为会话控制动作处理，而不是消息处理。
2. 不引入 `screen_changed` 作为独立消息类型。
3. 不引入复杂的消息分片模型。
4. 点击和滑动先仅通过 `message_type` 表达，不额外拆事件子表。
5. `speech_text` 是唯一允许创建新 `conversation` 的消息类型。

## 13. Server 续跑算法

每次新的事件到来时，Server 端建议按以下步骤执行：

1. 加载 `conversation`。
2. 根据当前 `user` message 的 `message_type` 判断是新建会话还是续接会话：
   - `speech_text`：创建新 `conversation`，必要时先关闭旧会话
   - `observed_click` / `observed_scroll`：只允许续接当前活跃且 `waiting_interaction` 的会话
3. 基于 `version` 或数据库事务避免并发续跑覆盖。
4. 创建新的 `run`，记录 `run_index` 和 `previous_run_id`。
5. 为这次 `run` 创建一条 `user` message：
   - 首轮语音时：`message_type = speech_text`
   - 点击续跑时：`message_type = observed_click`
   - 滑动续跑时：`message_type = observed_scroll`
6. 保存本次 `screen_snapshot`，截图仅在本次 `run` 内存中使用。
7. 组装模型输入，至少包含：
   - 当前 `conversation` 最近几条 `messages`
   - 当前会话的持续目标 `goal`，首轮等于本次 `speech_text`
   - 当前 `nodes`
   - 当前截图
   - 当前包名与尺寸信息
8. 执行模型调用。
9. 解析结构化结果并更新 `run`。
10. 为这次 `run` 创建一条 `assistant` message：
   - `message_type = guidance`
   - `text = 返回给用户的指导文案`
11. 按执行结果更新 `conversation.status`、`wait_started_at`、`updated_at` 与 `version`。
12. 返回给 Android 本次新的指导结果。

这里的关键点是：

1. Server 不依赖 Android 对“是否成功完成上一步”做判断。
2. Server 结合当前会话最近消息、持续目标 `goal` 和当前新页面，再决定下一步怎么走。
3. MVP 先按理想闭环实现：一次用户首轮语音启动一次完整 loop，下一次新语音不参与上一轮 loop 的上下文。

## 14. Android 续跑算法

Android 端在等待态建议采用以下简单流程：

1. 进入 `waiting_click_or_scroll`。
2. 只监听 `click` 与 `scroll` 两类交互事件。
3. 一旦监听到事件，进入 `capturing_followup_snapshot`。
4. 等待短稳定窗口，建议先用 `300ms - 800ms` 量级做去抖动。
5. 重新采集：
   - `packageName`
   - `activityName`
   - `nodes`
   - `screenshot`
   - `screenWidth / screenHeight`
   - `imageWidth / imageHeight`
6. 把新快照和本次交互消息一起上传给 Server。
7. 收到 Server 返回后继续高亮和播报。
8. 当会话处于 loop 中时，语音键可替换为中止键；如果用户点击中止键，客户端调用显式 `cancel` 路由结束当前会话，不再上传 `user_interrupt` message。

MVP 约束：

1. Android 不做复杂页面对比。
2. Android 不在本地判断这一步是否成功。
3. Android 不维护长 prompt 上下文。
4. Android 只负责续跑触发和事实采集。

## 15. 一条完整示例链路

以“点击底部我的”这个场景为例：

1. 用户说：“帮我找到订单入口。”
2. Android 采集首屏并发起第一次分析请求。
3. Server 创建：
   - `conversation #1`
   - `run #1`
   - `message #1 (role=user, message_type=speech_text, text=帮我找到订单入口)`
   - `screen_snapshot #1`
4. Server 返回：
   - `answer = 请先点击底部的我的`
   - `action.type = tap`
   - `target.label = 我的`
   - `message #2 (role=assistant, message_type=guidance, text=请先点击底部的我的)`
5. Android 高亮并播报，然后进入 `waiting_click_or_scroll`。
6. 用户点击底部 Tab。
7. Android 观察到 `click`，等待短稳定窗口后重新采集第二屏。
8. Android 发起第二次分析请求。
9. Server 创建：
   - `run #2`
   - `message #3 (role=user, message_type=observed_click, text=null)`
   - `screen_snapshot #2`
10. Server 对比最近消息 + 上一步输出 + 当前新页面，再决定下一步，例如“点击订单”。
11. Server 创建：
   - `message #4 (role=assistant, message_type=guidance, text=现在点击订单)`

如果此时用户又说了一句新的 `speech_text`，例如“不要找订单了，帮我找设置”，则：

1. 旧 `conversation #1` 被标记为 `cancelled`
2. `closed_reason = replaced_by_new_speech`
3. Server 创建新的 `conversation #2`
4. 新语音消息归属于 `conversation #2`

整个过程中：

1. 用户不需要再说第二句话。
2. Android 不需要本地判断用户是否点对。
3. 用户如果想放弃当前任务，可以直接点击中止键结束会话。
4. Server 不需要保持一个长时间阻塞的 loop。

## 16. 对后续接口设计的影响

当前 `contracts/openapi/analyze.yaml` 仍是单轮合同。

如果按本 RFC 实施多轮 loop，后续外部合同大概率需要增加如下可选字段：

1. `conversationId`
2. `runId` 或 `previousRunId`
3. `messageType`
4. `messageText`

但本 RFC 本身不直接修改外部合同。

后续若正式扩展对外协议，应单独新增合同 RFC，并同步更新：

1. `contracts/openapi/`
2. Android 请求构造逻辑
3. Server 路由输入校验逻辑

## 17. 非目标与延期项

以下内容明确不纳入本 MVP：

1. mid-loop 自由语音打断并继续同一会话。
2. `click + scroll + screen_changed + timeout` 的复杂多事件编排。
3. `message_part` 的细粒度模型消息存储。
4. Android 本地语义成功判定。
5. Redis 会话缓存或分布式锁。
6. 长会话记忆、知识库、embedding、评估表。

这些能力都可能在后续版本需要，但不应阻塞第一版主链路落地。

## 18. 最终建议

MVP 阶段建议立即按以下最小方案建设：

1. 数据库先落：`conversations`、`runs`、`messages`、`screen_snapshots`。
2. 会话状态先落：`analyzing`、`waiting_interaction`、`completed`、`failed`、`cancelled`、`expired`。
3. Android 续跑交互先落：`speech_text`、`observed_click`、`observed_scroll`。
4. Server 实现上坚持“一次事件，一次短 run”，不要做长驻内存 loop。
5. `message_part` 和中途更复杂语音交互，放到下一阶段单独 RFC。

这样做的好处是：

1. 结构足够小，能尽快开工。
2. 会话、消息、执行记录三层边界清楚，足够支撑当前 MVP。
3. 截图默认不持久化，可以显著降低实现复杂度和隐私成本。
4. 后续扩展 mid-loop 语音和更复杂 agent 时，不需要推倒重来。
