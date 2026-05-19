# Server 数据库搭建 RFC：PostgreSQL + Drizzle

## 1. 文档目标

本 RFC 用于确定 Server 端第一版数据库技术选型与落地方式，重点回答以下问题：

1. 当前项目数据库应该选什么。
2. ORM / 查询层应该选什么。
3. schema 和 migration 应该如何组织。
4. 如何承接当前 `conversation / run / message / screen_snapshot` 这套数据模型。

本文件的结论主要服务于：

1. `server/` 侧持久化层建设。
2. 第一版 PostgreSQL schema 初始化。
3. 后续 migration 规范和开发约束统一。

## 2. 背景

当前仓库中的 `server` 仍未接入数据库能力。

但随着 agent loop 数据模型已经收敛到：

1. `conversations`
2. `runs`
3. `messages`
4. `screen_snapshots`

Server 已经需要一套正式的持久化基础设施来承接：

1. 会话状态管理
2. loop 续接判断
3. 并发保护
4. 运行记录回放与排查

当前阶段的重点不是复杂数据平台，而是：

1. 选一套简单、透明、适合 TypeScript 的数据库方案
2. 能快速支撑 schema 频繁调整
3. 不引入过重抽象

## 3. 结论

本 RFC 的技术选型结论如下：

1. 数据库选型：`PostgreSQL`
2. TypeScript 数据访问层选型：`Drizzle ORM`
3. migration 方式：`Drizzle migration + SQL 文件`
4. schema 真源：`server/` 内的 Drizzle TypeScript schema 定义
5. 当前阶段不引入 Atlas HCL、Prisma、重型 Repository 框架

## 4. 为什么选 PostgreSQL

当前项目使用 PostgreSQL 的原因如下：

1. 关系模型清晰，适合当前会话、消息、执行记录这类强结构化数据。
2. 对事务、唯一约束、部分唯一索引、JSONB 都支持良好。
3. 当前 schema 中有多处明显适合 PostgreSQL 的能力：
   - `jsonb`
   - 部分唯一索引
   - 乐观锁更新
   - 未来可能的事务续接控制
4. 后续如果需要统计、回放、排查、评估，也更容易扩展。

本项目当前不建议：

1. 先上 SQLite 作为 Server 正式库
2. 先上文档型数据库
3. 先上分布式数据库或多租户复杂架构

## 5. 为什么选 Drizzle

当前阶段选择 Drizzle 的主要原因如下：

1. `TypeScript` 生态集成自然。
2. schema 可以直接用 TS 表达，便于和当前 server 项目一起维护。
3. 相比重型 ORM，Drizzle 更轻，更接近 SQL。
4. 相比完全手写 SQL 访问层，Drizzle 可以提供基础类型安全和更一致的表定义。
5. 当前表数量不多，关系也不复杂，Drizzle 足够支撑 MVP。

当前阶段不优先选择其他方案的原因：

### 5.1 不优先 Prisma

1. 当前项目不是典型 CRUD 后台。
2. 后续大概率会写较多条件续接、状态机更新和事务控制逻辑。
3. Prisma 在这类场景下不一定比 Drizzle 更顺手。

### 5.2 不优先 Atlas HCL

1. 当前 schema 仍处于快速收敛阶段。
2. 当前最需要的是简单、直接、透明，而不是额外 DSL。
3. Atlas 更适合放到后续 schema 治理阶段，而不是当前 MVP 的主 schema 入口。

### 5.3 不直接用裸 `pg`

1. 完全手写 SQL 查询层会让类型保护过弱。
2. 当前项目仍希望保持最小但明确的 schema 定义中心。

## 6. 总体设计原则

数据库搭建遵守以下原则：

1. 先服务当前 agent loop 主链路。
2. schema 要和 `docs/20260519-server-agent-loop-mvp-rfc.md` 保持一致。
3. 优先简单、显式、容易 review。
4. migration 必须可追踪、可重放、可回滚思考。
5. 不为未来做过度平台化设计。

## 7. 当前数据库范围

第一版数据库只覆盖以下正式表：

1. `conversations`
2. `runs`
3. `messages`
4. `screen_snapshots`

第一版明确不做：

1. `files`
2. `message_parts`
3. 向量表
4. 审计表
5. 评估表
6. 任务队列表

## 8. 推荐目录结构

建议在 `server/` 下新增如下结构：

```text
server/
  drizzle.config.ts
  drizzle/
    migrations/
  src/
    db/
      client.ts
      schema/
        conversations.ts
        runs.ts
        messages.ts
        screen-snapshots.ts
        index.ts
      queries/
        conversations.ts
        runs.ts
        messages.ts
        screen-snapshots.ts
      transactions/
        continue-conversation.ts
```

说明：

1. `schema/` 只负责表结构定义。
2. `queries/` 只负责轻量查询封装。
3. `transactions/` 放需要事务一致性的续接逻辑。
4. 不建议一开始就做重型 repository 层。

## 9. Drizzle 配置建议

### 9.1 驱动

建议使用 PostgreSQL 官方生态常见驱动，例如：

1. `pg`
2. `drizzle-orm`

### 9.2 配置文件

建议使用：

- `server/drizzle.config.ts`

配置职责：

1. 指定 schema 文件入口
2. 指定 migration 输出目录
3. 读取数据库连接字符串

### 9.3 环境变量

建议至少使用：

1. `DATABASE_URL`

如需区分环境，可后续再加：

1. `DATABASE_URL_TEST`
2. `DATABASE_URL_LOCAL`

## 10. migration 策略

### 10.1 总原则

1. migration 必须入库到版本控制。
2. migration 文件必须按时间顺序创建。
3. 不允许只改 schema 文件但不产出 migration。
4. 生产库变更必须通过 migration 推进，不允许手工改库后不补回迁移。
5. 当前采用 `schema-first`：Drizzle schema 是逻辑真源，migration 是由 schema 生成并提交到仓库的可执行产物。

### 10.2 推荐方式

第一版建议：

1. 用 Drizzle schema 定义表
2. 生成 migration 文件
3. 将 migration 文件作为最终评审对象之一
4. 如果为 PostgreSQL 特性手工调整 migration，必须同步更新 Drizzle schema，保持一致

### 10.3 为什么不是 DB-first

当前不建议先手工建库再反向同步 schema，原因是：

1. 初期 schema 变化频繁
2. 项目当前更适合把数据库定义和 server 代码放在一起维护

## 11. 事务策略

当前 schema 的事务重点不在复杂业务，而在会话续接一致性。

第一版建议重点保证以下场景的事务原子性：

1. 续接已有 `conversation` 时的状态检查与 `run` 创建
2. `message` 与 `run`、`screen_snapshot` 的成组写入
3. 新 `speech_text` 到来时，关闭旧会话并开启新会话
4. 用户点击中止键后，通过 `cancel` 路由将会话置为 `cancelled / user_exit`

这些逻辑不建议散落在 route handler 中，应收敛在 `transactions/` 或 service 层。

## 12. 与当前业务模型的映射

本节只说明数据库搭建层如何承接当前 RFC 中的数据模型，不重新定义业务语义。

### 12.1 `conversations`

Drizzle schema 需要表达：

1. 会话主键
2. 设备标识
3. 包名
4. 状态
5. 乐观锁版本号
6. 关闭原因

重点约束：

1. 同一设备同一时刻最多一个活跃会话
2. 活跃会话的定义由状态决定：
   - `analyzing`
   - `waiting_interaction`

这里建议通过 PostgreSQL 部分唯一索引表达，而不是完全依赖业务代码。

### 12.2 `runs`

Drizzle schema 需要表达：

1. 所属 `conversation_id`
2. 会话内递增 `run_index`
3. `previous_run_id`
4. 执行状态
5. 模型信息
6. 输入上下文 JSON
7. 原始输出 JSON
8. 结构化输出字段

重点约束：

1. `conversation_id + run_index` 唯一
2. `previous_run_id` 自关联可空

### 12.3 `messages`

Drizzle schema 需要表达：

1. 所属 `conversation_id`
2. 所属 `run_id`
3. `role`
4. `message_type`
5. 可空文本内容

当前 MVP 的消息类型固定为：

1. `speech_text`
2. `observed_click`
3. `observed_scroll`
4. `guidance`

重点约束：

1. 每个 `run` 最多一条 `user` message
2. 每个 `run` 最多一条 `assistant` message

这里有两种实现方式：

1. 先只在服务层保证
2. 或通过部分唯一索引约束：
   - `run_id where role = 'user'`
   - `run_id where role = 'assistant'`

MVP 推荐：

1. 先在服务层保证
2. 如果 Drizzle migration 编写复杂度可控，再补部分唯一索引

### 12.4 `screen_snapshots`

Drizzle schema 需要表达：

1. 所属 `conversation_id`
2. 所属 `run_id`
3. 包名和 Activity
4. 尺寸信息
5. `nodes_json`
6. 采集时间

重点约束：

1. `run_id` 唯一，保证一条 `run` 对应一份页面快照

## 13. JSONB 使用原则

当前 schema 中推荐使用 `jsonb` 的字段：

1. `runs.input_context_json`
2. `runs.response_raw_json`
3. `runs.result_target_bounds_json`
4. `screen_snapshots.nodes_json`

原因：

1. 当前输入和输出结构仍在快速演进
2. 没必要过早拆成大量子表
3. PostgreSQL 的 `jsonb` 足够承接当前阶段的灵活字段

但要明确：

1. `jsonb` 不是替代正式结构化字段的借口
2. 会被高频过滤、排序、约束的字段，仍应保持独立列

## 14. 并发与锁策略

当前 MVP 不建议上复杂分布式锁。

建议策略：

1. `conversations.version` 做乐观锁控制
2. 关键续接流程包事务
3. 必要时在单条 `conversation` 上做 `for update`

适用场景：

1. 两次点击几乎同时上报
2. 新语音和旧续接请求竞争
3. 超时判断和续接同时发生

## 15. 命名与类型约束

建议统一遵守以下规则：

1. 表名使用复数：`conversations`、`runs`、`messages`、`screen_snapshots`
2. 主键统一使用 `uuid`
3. 时间统一使用 `timestamptz`
4. 状态和消息类型优先用 PostgreSQL enum 或 Drizzle enum 表达
5. 计数类字段用 `integer`
6. JSON 结构统一用 `jsonb`

## 16. Server 集成方式

### 16.1 `src/db/client.ts`

职责：

1. 初始化 PostgreSQL 连接
2. 初始化 Drizzle client
3. 暴露统一 `db` 入口

### 16.2 `src/db/schema/`

职责：

1. 定义所有表结构
2. 定义枚举
3. 定义关系

### 16.3 `src/db/queries/`

职责：

1. 放简单直接的查询函数
2. 避免 route 中直接写 SQL 细节

### 16.4 `src/db/transactions/`

职责：

1. 放需要原子性的业务写入流程
2. 例如：
   - `startConversationFromSpeech`
   - `continueConversationFromObservedClick`
   - `cancelConversationByUserExit`

说明：

1. `cancelConversationByUserExit` 对应客户端点击中止键后调用的显式 `cancel` 路由。

## 17. 开发流程建议

推荐日常开发流程：

1. 先改 RFC 或数据模型说明
2. 再改 Drizzle schema
3. 生成 migration
4. 本地执行 migration
5. 修改 service / transaction 逻辑
6. 补测试

不要反过来：

1. 先手改数据库
2. 再尝试补 schema

## 18. 第一版实施顺序

建议按以下顺序落地：

1. 安装 PostgreSQL 驱动与 Drizzle 依赖
2. 增加 `drizzle.config.ts`
3. 创建 `src/db/schema/` 基础文件
4. 先落 `conversations` 表
5. 再落 `runs`
6. 再落 `messages`
7. 最后落 `screen_snapshots`
8. 生成并执行首个 migration
9. 在 `server` 中接入最小数据库 client

## 19. 不纳入本 RFC 的内容

本 RFC 不覆盖：

1. 生产数据库部署方式
2. 备份策略
3. 多环境数据库托管服务选择
4. 连接池细节调优
5. analytics / BI 方案

这些内容后续如有需要，再单独新增基础设施 RFC。

## 20. 最终建议

当前阶段建议直接采用：

1. `PostgreSQL`
2. `Drizzle ORM`
3. `Drizzle migration`
4. `schema-first`
5. 按 `conversation / run / message / screen_snapshot` 四表先落地

这样做的好处是：

1. 与当前 TypeScript server 技术栈一致。
2. 相比 Atlas HCL 更直接，当前更容易迭代。
3. 相比重 ORM 更轻，能保持 schema 和查询足够透明。
4. 足够支撑当前 agent loop MVP，而不会过度设计。
