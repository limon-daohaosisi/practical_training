# 大任务 5：服务端分析链路

## 任务目标

实现服务端从接收客户端请求，到调用模型分析，再到返回稳定结构化结果的完整链路。

这个任务的重点不是“做一个会聊天的 AI”，而是“稳定返回可用于高亮和播报的结构化指令”。

## 建议负责人类型

- 主负责人：后端 / AI 工程师

## 前置依赖

- 大任务 1 已完成 server 骨架和 schema 初稿
- Android 侧至少已产出一份真实或 mock 请求样本

## 范围

包含：

- `ingestion` 接口
- `ui-normalizer`
- `screen-analyzer`
- OpenAI `Responses API` 接入
- 结构化输出 schema
- `response-validator`
- `instruction-render-helper`

不包含：

- 第二阶段复杂 agent 编排
- Realtime
- 复杂缓存系统

## 输入

- 截图
- 节点树
- OCR 文本
- 用户问题
- 设备信息

## 输出

- 稳定结构化响应
- 可供 Android 直接消费的 `target.bounds`、`answer`、`action`、`confidence`

## 子任务拆分

1. 建立请求接收路由。
2. 定义并校验请求 schema。
3. 实现节点树压缩和关键节点提取。
4. 实现 OCR 和节点信息的摘要拼接。
5. 编写模型系统提示词和调用逻辑。
6. 定义结构化输出 schema。
7. 调用模型并解析结果。
8. 校验 `bounds`、`confidence`、字段完整性。
9. 输出客户端直接可消费的结果结构。
10. 准备错误响应和降级响应。

## 验收标准

- 对给定样本请求，服务端能稳定返回符合 schema 的 JSON。
- 返回结果至少包含可用的 `answer`、`target.bounds`、`action`、`confidence`。
- 非法或缺失字段会被拦截，而不是直接透传给客户端。
- Android 团队可以基于服务端返回值直接联调。

## 依赖和解耦建议

- Android 未打通前，先用本地样本请求开发。
- 模型尚未接入时，先返回固定 mock 响应验证整体格式。
- 先把 schema 和 validator 做稳，再优化 prompt。

## 典型风险

- 模型输出不稳定。
- 节点树过大导致提示词冗长。
- 坐标越界或格式不统一导致客户端无法渲染。

## 管理检查点

- 验收时要看真实输入样本和真实输出 JSON，不只看“接口通了”。
- 重点检查 schema 稳定性，而不是只看某一次结果是否聪明。
- 让后端明确展示错误处理和降级策略。
