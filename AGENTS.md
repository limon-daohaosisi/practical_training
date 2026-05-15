# AGENTS.md

## 适用范围

本文件适用于整个仓库。

## 项目概览

本仓库包含一个 Android AI 引导系统，顶层主要分为 3 个部分：

- `android-app/`：Android 客户端
- `server/`：TypeScript 后端分析服务
- `contracts/`：跨语言外部接口合同

配套资料位于：

- `docs/`：产品与技术规划文档

## 工作优先级

进行改动时，按以下优先级决策：

1. 保证 Android -> Server -> 引导结果 这条主链路正确。
2. 保持 Android、server、contracts 之间的边界清晰。
3. 避免同一份外部 API 合同在多个位置重复维护。
4. 优先选择简单、面向 MVP 的实现，不做超前抽象。

## 真源约定

- 外部 API 合同归属 `contracts/openapi/`
- 产品和架构意图归属 `README.md` 与 `docs/`
- server 内部实现细节归属 `server/src/`
- Android 端实现细节归属 `android-app/`

不要在多个位置手工维护同一份外部请求/响应合同。

## 执行规则

在进行非微小改动前：

1. 如果当前子目录下有本地 `AGENTS.md`，先阅读对应文件。
2. 如果任务涉及架构或目录约定，先检查 `README.md`。
3. 如果任务涉及外部 API 行为，先检查 `contracts/openapi/`。

在完成改动后：

1. 如果架构、目录结构、合同约定发生变化，同步更新文档。
2. 运行与改动区域最相关的校验。
3. 如果未能运行校验，必须明确说明。

## 校验要求

对于 `server/` 改动：

- 默认优先运行 `server/` 下的 `pnpm run check` 作为完整基础校验。
- 如果只需要快速验证类型，可先运行 `pnpm run typecheck`。
- 如果后续添加了测试，尽量运行与改动相关的 server 测试。

对于 `android-app/` 改动：

- 默认优先在 Android Studio 中完成 Gradle 同步。
- 如果命令行校验可行，可在 `android-app/` 下运行 `./gradlew assembleDebug`。
- 如果当前环境不适合做 Android 校验，必须明确说明。

对于 `contracts/` 改动：

- 检查合同是否仍与当前 server 和 Android 端预期一致。
- 保持 examples、命名和真实 API 结构一致。

## 仓库边界

- 不要把 server 业务逻辑放进 `contracts/`
- 不要把外部 API 合同真源放进 Android 私有文件或 server 私有文件
- 不要把 `node_modules/`、构建产物、生成文件当作源码修改

## 改动风格

- 保持 route handler 足够薄
- 保持 service 逻辑聚焦且可组合
- MVP 阶段的 agent 编排放在 server 内，不单独拆成独立服务
- 只有在当前用例真实需要时才增加新结构

## 协作说明

当改动跨越仓库多个部分时：

1. 先判断这次改动是 contract-first、server-first 还是 Android-first。
2. 如果外部 API 有变化，优先更新 `contracts/openapi/`，或与实现放在同一次改动中完成。
3. 如果某一侧被阻塞，优先使用 mock 数据或占位响应解耦，而不是原地等待。
