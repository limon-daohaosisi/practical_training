# AGENTS.md

## 适用范围

本文件适用于 `contracts/` 目录。

## 目录职责

`contracts/` 用于存放跨端共享的外部接口合同。

在本项目中，它的核心作用是：

1. 作为 Android 客户端与 Server 之间的外部协议真源
2. 统一定义请求结构、响应结构、错误结构和接口语义
3. 作为联调、测试、文档和后续代码生成的基础

当前第一版主要使用：

- `contracts/openapi/`

## 边界定义

`contracts/` 只负责“外部协议”。

这里应该放：

- OpenAPI 合同
- 外部请求/响应结构定义
- 错误码和状态码约定
- multipart / JSON / 文件上传等接口层协议约定
- Android 与 Server 必须共同遵守的字段语义

这里不应该放：

- Server 业务逻辑
- Android UI 逻辑
- Agent 内部推理流程
- Prompt 实现细节
- Server 内部中间结构
- 测试代码或运行时代码

## 真源原则

对于 Android <-> Server 的外部 HTTP 协议：

- 真源在 `contracts/openapi/`

不要在以下位置手工维护另一份平行真源：

- `server/src/`
- `android-app/`
- `docs/` 中的普通说明性文字

文档可以解释合同，但不能替代合同。

## 修改原则

修改 `contracts/` 时，优先保证：

1. 字段含义清晰
2. 必填/可选边界清晰
3. 坐标系、单位、顺序、枚举值定义清晰
4. 错误码与 HTTP 状态码关系清晰
5. Android 与 Server 都能按同一理解实现

如果一个字段还没有稳定共识，不要为了“提前设计”把它塞进正式合同。

## 与其他目录的关系

### 与 `docs/`

- `docs/` 负责 RFC、规划和解释
- `contracts/` 负责最终可执行的外部协议定义

通常流程应是：

1. 在 `docs/` 中形成 RFC 或设计结论
2. 再将结论落实到 `contracts/`

### 与 `server/`

- `server/` 必须实现 `contracts/` 中定义的对外协议
- `server` 内部可以有自己的中间结构，但不能和 `contracts/` 冲突

### 与 `android-app/`

- Android 请求和响应处理必须以 `contracts/` 为准
- Android 端不能自行发明一套不同字段名或不同语义

## 当前项目约束

当前第一版 `analyze` 合同已经明确：

1. `POST /analyze` 使用 `multipart/form-data`
2. 请求分为 `metadata` 和 `screenshot`
3. `nodes.bounds` 使用屏幕坐标系
4. `target.bounds` 也必须返回屏幕坐标系

后续修改这些核心规则时，必须同步更新：

- 对应 RFC
- `contracts/openapi/analyze.yaml`
- 相关实现方的消费逻辑

## 审查清单

修改 `contracts/` 后，至少自查以下问题：

1. 这是不是外部协议，而不是内部实现细节
2. 有没有和现有 RFC 结论冲突
3. Android 和 Server 是否都能按此实现
4. 是否引入了不必要的未来字段
5. 状态码、错误码、坐标系是否说清楚

## 修改风格

- 优先精简
- 优先明确
- 优先可联调
- 不追求一次性覆盖未来全部接口

对于 MVP，合同应该先服务当前主链路，而不是替未来所有版本做预埋设计。
