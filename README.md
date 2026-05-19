# practical_training

## 系统架构

这是一个面向 Android 的 AI 辅助引导系统，目标是在用户使用第三方 App 时，理解当前页面，并通过高亮和语音提示指导用户完成下一步操作。

系统整体采用单仓库、多子目录的结构，核心分为 3 层：

1. Android 客户端
2. Server 分析服务
3. 协议与文档层

## 推荐目录结构

```text
practical_training/
  README.md
  docs/
  contracts/
    openapi/
  android-app/
  server/
    src/
      __test__/
      routes/
      agents/
      services/
      plugins/
      utils/
```

## 架构分层

### 1. Android 客户端

目录：

- [android-app](/home/daohaosisi/dev/practical_training/android-app)

职责：

- 权限引导
- 无障碍服务接入
- 当前页面节点树采集
- 当前页面截图
- 本地 OCR
- 用户语音输入
- 屏幕高亮和提示气泡
- TTS 语音播报
- 会话状态管理

这一层负责“看见当前页面、听见用户问题、把服务端结果展示给用户”。

### 2. Server 分析服务

目录：

- [server](/home/daohaosisi/dev/practical_training/server)

职责：

- 接收 Android 端上传的截图、节点树、OCR 文本和用户问题
- 标准化 UI 数据
- 调用模型进行分析
- 返回结构化指令结果
- 做基础结果校验
- 为后续 agent loop 预留编排层

这一层负责“理解页面并产出下一步引导建议”。

### 3. 协议与文档层

目录：

- [docs](/home/daohaosisi/dev/practical_training/docs)
- [contracts](/home/daohaosisi/dev/practical_training/contracts)

职责：

- 保存产品规划和技术文档
- 定义 Android 与 Server 之间的外部接口合同
- 作为跨语言协作的统一协议来源

这里建议将 Android 与 Server 的外部 API 协议单独放到 `contracts/openapi/` 中，用 OpenAPI 作为唯一合同来源。

## 核心数据流

系统的核心链路如下：

1. 用户在第三方 App 中发起辅助请求。
2. Android 客户端通过 `AccessibilityService` 采集当前页面节点树。
3. Android 客户端获取当前页面截图，并补充本地 OCR 结果。
4. 用户通过语音提出问题，客户端得到文本。
5. 客户端将截图、节点树、OCR 文本、问题、设备信息发送到 Server。
6. Server 对输入做标准化处理，并交给模型分析。
7. Server 返回结构化结果，例如目标区域、操作建议、说明文案和置信度。
8. Android 客户端根据返回坐标在屏幕上高亮目标区域，并通过 TTS 播报提示。

## Server 内部建议结构

第一版 Server 建议采用：

- `routes/`
  负责接收请求和返回响应
- `agents/`
  负责带 loop 的分析编排逻辑
- `services/`
  负责 UI 标准化、结果校验、结果整理等纯业务逻辑
- `plugins/`
  负责环境配置和 OpenAI client 等基础设施接入

说明：

- `agents/` 放在 `server` 内部，而不是单独拆成独立服务
- 第一版重点是跑通主链路，不先拆成多服务架构

## 协议设计原则

本项目是 `Kotlin Android + TypeScript Server` 的跨语言协作场景，因此不建议把外部接口定义只写在某一端代码中。

推荐原则：

1. Android 和 Server 的外部接口协议只保留一份真源。
2. 这份真源建议放在 `contracts/openapi/`。
3. Server 内部自己的中间结构可以单独定义，但不应重复手写外部请求和响应合同。

## MVP 边界

第一版只聚焦主链路验证：

- 页面采集
- 截图
- OCR
- 语音问题输入
- Server 分析
- 屏幕高亮
- TTS 播报

第一版暂不包含：

- 自动代点
- 自动填表
- 长时间后台监听
- 连续录屏分析
- 远程协助
- iOS 版本

## 环境与依赖安装

本仓库当前分为 Android 客户端和 Server 两部分，两边的依赖安装方式不同。

### 1. Server 依赖安装

前置要求：

- `Node.js 20+`
- `pnpm`
- `PostgreSQL`

安装命令：

```bash
cd server
pnpm install
```

开发运行：

```bash
cd server
pnpm run dev
```

### 1.1 Server 数据库使用

Server 当前使用：

- `PostgreSQL`
- `Drizzle ORM`
- `drizzle.config.ts`
- `server/src/db/schema/` 作为 schema 真源

初始化前请先准备数据库，并设置环境变量：

```bash
cd server
export DATABASE_URL="postgres://user:password@127.0.0.1:5432/practical_training"
```

说明：

- `DATABASE_URL` 是当前 Drizzle 配置和数据库 client 的必要环境变量
- 未设置 `DATABASE_URL` 时，`db:generate`、`db:migrate` 和运行时数据库初始化都会失败

当前数据库相关目录：

```text
server/
  drizzle.config.ts
  drizzle/
    migrations/
  src/
    db/
      client.ts
      schema/
```

常用命令：

生成 migration：

```bash
cd server
DATABASE_URL="postgres://user:password@127.0.0.1:5432/practical_training" pnpm run db:generate
```

执行 migration：

```bash
cd server
DATABASE_URL="postgres://user:password@127.0.0.1:5432/practical_training" pnpm run db:migrate
```

建议开发顺序：

1. 先修改 `server/src/db/schema/`
2. 再生成 migration
3. 再执行 migration
4. 最后修改依赖这些表的 route / service / transaction 逻辑

当前 MVP 阶段的数据库约束：

- 正式真源在 `server/src/db/schema/`
- migration 文件必须提交到仓库
- 不要手工改库后不补回 schema 和 migration
- 截图不作为正式数据库真源，只在单次 run 内存中使用

类型检查：

```bash
cd server
pnpm run typecheck
```

Lint：

```bash
cd server
pnpm run lint
```

格式化检查：

```bash
cd server
pnpm run format:check
```

测试：

```bash
cd server
pnpm run test
```

构建：

```bash
cd server
pnpm run build
```

统一校验：

```bash
cd server
pnpm run check
```

说明：

- `server/package.json` 已包含第一版基础依赖
- 当前已包含 Fastify 骨架与 PostgreSQL/Drizzle 基础设施
- 当前 server 已包含 `typecheck`、`ESLint`、`Prettier`、`Vitest` 基础校验
- 后续接入 OpenAI、更多 transaction 与 query 层能力时，再继续补充依赖和测试

### 2. Android 依赖安装

Android 端当前位于 [android-app](/home/daohaosisi/dev/practical_training/android-app)。

Android 端通常不通过 `npm` 安装依赖，而是通过 `Gradle` 管理。

推荐方式：

1. 使用 Android Studio 打开 `android-app/`
2. 等待 Gradle 自动同步
3. 如果没有自动同步，手动点击 `Sync Project with Gradle Files`

如果需要命令行构建，可在 `android-app/` 下执行：

```bash
cd android-app
./gradlew assembleDebug
```

Android 端格式检查：

```bash
cd android-app
./gradlew ktlintCheck
```

Android 端自动格式化：

```bash
cd android-app
./gradlew ktlintFormat
```

Android Lint：

```bash
cd android-app
./gradlew lintDebug
```

Android 单元测试：

```bash
cd android-app
./gradlew testDebugUnitTest
```

Android 聚合校验：

```bash
cd android-app
./gradlew androidCheck
```

说明：

- Android 端当前已接入 `ktlint`
- `androidCheck` 会聚合执行：
  - `ktlintCheck`
  - `lintDebug`
  - `testDebugUnitTest`
  - `assembleDebug`
- 如果执行复杂任务或改动多个 Android 文件，建议在交接前运行一次 `./gradlew ktlintFormat`

## 当前状态

- Android 工程已初始化在 [android-app](/home/daohaosisi/dev/practical_training/android-app)
- Server 基础骨架已初始化在 [server](/home/daohaosisi/dev/practical_training/server)
- 协议目录已初始化在 [contracts](/home/daohaosisi/dev/practical_training/contracts)
- 规划文档在 [docs/PROJECT_PLAN.md](/home/daohaosisi/dev/practical_training/docs/PROJECT_PLAN.md)
- 任务拆分文档在 [task](/home/daohaosisi/dev/practical_training/task)
