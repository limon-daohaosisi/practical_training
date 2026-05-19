# AGENTS.md

## 适用范围

本文件适用于 `server/src/`。

## Server 角色

`server/src/` 存放分析服务的后端应用代码。

server 负责：

- 接收 Android 客户端请求
- 标准化与页面相关的输入
- 编排分析流程
- 返回结构化引导结果

这一层不应包含 Android 专属 UI 逻辑。

## 架构规则

MVP 阶段的 server 应保持小而分层：

1. `main.ts` 负责启动进程
2. `app.ts` 负责构建和配置 Fastify 应用
3. `routes/` 负责 HTTP 入口
4. `agents/` 负责带 loop 的编排和更高层的推理流程
5. `services/` 负责纯业务逻辑和领域转换
6. `plugins/` 负责基础设施接线
7. `utils/` 负责小型无状态工具函数
8. `__test__/` 在需要时存放 server 测试与测试样本

## 目录边界

### `main.ts`

- 只做进程入口
- 读取 host / port 并启动服务
- 不放业务逻辑

### `app.ts`

- 构建 Fastify 应用
- 注册 routes 和共享基础设施钩子
- 只关注应用装配

### `routes/`

- 负责请求/响应层的传输处理
- 在 HTTP 边界做数据校验和解析
- 调用 orchestrator 或 service
- 返回结构化响应

当前 `routes/` 目录采用按路由分文件夹组织的三文件法：

- `xxx/xxx.route.ts`：路由注册
- `xxx/xxx.handler.ts`：handler 实现
- `xxx/xxx.schema.ts`：请求参数、请求体、响应结构的 schema 与类型

例如：

- `routes/health/health.route.ts`
- `routes/health/health.handler.ts`
- `routes/health/health.schema.ts`

应该：

- 保持 handler 足够薄
- 把传输层输入映射为应用调用
- 把应用结果映射为 HTTP 响应

不应该：

- 塞入大段业务逻辑
- 塞入 agent loop 逻辑
- 在多个 handler 中重复写标准化逻辑

### `agents/`

- 负责带 loop 的分析编排
- 协调重试、分阶段推理、工具调用和停止条件
- 尽量复用下层 services，而不是复制其逻辑

当某段逻辑在回答这些问题时，应放在这里：

- 分析步骤应该按什么顺序执行
- 是否需要再次调用模型
- 如何合并中间推理结果

不要把基础 HTTP 解析或简单 helper 放到这里。

### `services/`

- 负责可复用且不绑定 HTTP 传输层的领域逻辑
- 合适的例子包括：
  - UI 标准化
  - 响应校验
  - 指令结构整理
  - 后续的领域级风险检查

services 应比 route handler 更容易单独测试。

### `plugins/`

- 负责基础设施注册和共享运行时配置
- 合适的例子包括：
  - env 加载
  - OpenAI client 初始化
  - 后续的 auth、logging、config 注册

plugins 提供的是基础设施，不应承载业务决策。

### `db/`

- `db/client.ts` 负责数据库连接与 Drizzle client 初始化
- `db/schema/` 负责数据库 schema 真源
- `db/queries/` 负责轻量查询封装
- `db/transactions/` 负责需要原子性的会话与运行写路径

数据库相关代码应遵守：

- 先改 `schema/`，再改 migration，再改依赖这些表的业务逻辑
- 不要把复杂业务决策塞进 `client.ts`
- 不要在 route handler 中直接散写数据库状态机逻辑

### `utils/`

- 只放小型、通用、无状态 helper
- 如果某个 helper 开始带有明显领域含义，应移动到 `services/` 或 `agents/`

不要把 `utils/` 变成杂项收纳箱。

### `__test__/`

- 用于存放 server 测试、fixtures、samples
- 优先编写覆盖 services 和 routes 行为的测试
- 测试数据应尽量贴近 Android 客户端真实 payload

## 合同边界

外部 API 合同不归 `server/src/` 所有。

Android <-> Server HTTP 合同的真源在：

- `contracts/openapi/`

在 `server/src/` 内部可以定义内部类型和内部校验结构，但不要为同一份外部合同再手工维护第二份真源。

## 实现准则

- 优先使用显式类型和可预测的返回结构
- 文件名应与真实职责对齐
- 优先组合，而不是深继承或厚重框架模式
- MVP 先围绕一条主分析路径建设

## 校验准则

修改 server 源码后，默认优先运行：

```bash
cd server
pnpm run check
```

如果执行了复杂任务，或对多个文件进行了连续修改，在交接前还应主动运行格式化：

```bash
cd server
pnpm run format
```

如果只需要快速验证某一类问题，也可按需运行：

```bash
cd server
pnpm run typecheck
pnpm run lint
pnpm run test
```

如果修改了数据库 schema 或 migration，还应优先检查：

```bash
cd server
DATABASE_URL="postgres://user:password@127.0.0.1:5432/practical_training" pnpm run db:generate
DATABASE_URL="postgres://user:password@127.0.0.1:5432/practical_training" pnpm run db:migrate
```

说明：

- `DATABASE_URL` 是数据库命令和运行时数据库初始化的必要环境变量
- 如果当前环境没有数据库，最终交接时必须说明 migration 未实际执行

如果当前无法做校验，最终交接时必须明确说明。
