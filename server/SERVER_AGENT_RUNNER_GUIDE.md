# Server 推理面开发与使用说明

本文档说明当前 `server` 侧真实推理面 runner 的开发状态、环境变量、启动方式、测试方式和已知限制。

## 当前能力

当前 server 已支持两种 `AgentRunner`：

- `mock`：默认 runner，不调用外部模型，适合接口合同和本地快速测试。
- `openai-compatible`：真实 runner，调用 vivo 提供的 OpenAI 兼容 `chat/completions` 接口。

实际启动时由环境变量 `AGENT_RUNNER` 决定：

```bash
AGENT_RUNNER=mock
AGENT_RUNNER=openai-compatible
```

如果不设置 `AGENT_RUNNER`，默认使用 `mock`。

## 环境变量

真实 runner 需要以下环境变量：

```bash
export OPENAI_API_KEY="你的 vivo AppKey"
export OPENAI_BASE_URL="https://api-ai.vivo.com.cn/v1"
export OPENAI_MODEL="Volc-DeepSeek-V3.2"
export OPENAI_INCLUDE_SCREENSHOT=false
```

说明：

- `OPENAI_API_KEY` 填 vivo 文档里的 `AppKey`。
- `OPENAI_BASE_URL` 建议填 `https://api-ai.vivo.com.cn/v1`。
- `OPENAI_MODEL` 填账号有权限的模型名。
- `OPENAI_INCLUDE_SCREENSHOT=false` 表示只把节点树和上下文发给模型。
- `OPENAI_INCLUDE_SCREENSHOT=true` 会把截图作为 base64 `image_url` 传给模型，但当前测试发现所用模型不支持图片输入。

建议本地创建不提交的 `server/.env.local`：

```bash
export OPENAI_API_KEY="你的 vivo AppKey"
export OPENAI_BASE_URL="https://api-ai.vivo.com.cn/v1"
export OPENAI_MODEL="Volc-DeepSeek-V3.2"
export OPENAI_INCLUDE_SCREENSHOT=false
```

不要提交 `.env.local`。

## 启动方式

进入 server 目录：

```bash
cd /home/gs_cs/proj/practical_training/server
```

使用 mock runner：

```bash
pnpm run dev
```

使用真实 vivo/OpenAI 兼容 runner：

```bash
source .env.local

AGENT_RUNNER=openai-compatible pnpm run dev
```

如果想显式关闭截图输入：

```bash
source .env.local

OPENAI_INCLUDE_SCREENSHOT=false \
AGENT_RUNNER=openai-compatible \
pnpm run dev
```

启动成功后会监听 `3000` 端口，例如：

```text
http://127.0.0.1:3000
```

健康检查：

```bash
curl http://127.0.0.1:3000/health
```

## vivo 接口对齐

当前真实 runner 对齐 vivo 文档中的接口：

```text
POST https://api-ai.vivo.com.cn/v1/chat/completions
```

请求特点：

- Header 使用 `Authorization: Bearer AppKey`。
- 每次请求都会自动带 `request_id=<uuid>` 查询参数。
- 请求体使用非流式：

```json
{
  "stream": false
}
```

响应读取：

```text
choices[0].message.content
```

模型必须返回 JSON。runner 会把模型输出解析成内部 `AgentRunOutput`。

## 输出契约

真实 runner 返回结构必须符合：

```ts
type AgentRunOutput = {
  answer: string;
  action: {
    type: "tap" | "scroll" | "none";
  };
  target: {
    label: string;
    bounds: {
      left: number;
      top: number;
      right: number;
      bottom: number;
    };
  } | null;
  shouldContinue: boolean;
};
```

校验规则：

- `tap` 必须有 `target`。
- `scroll` 和 `none` 会统一归一化为 `target = null`。
- `target.bounds` 必须在屏幕范围内。
- 非 JSON、字段缺失、非法 action、坐标越界都会降级。

降级输出：

```json
{
  "answer": "我暂时无法可靠定位下一步操作，请返回上一页或换个说法再试。",
  "action": { "type": "none" },
  "target": null,
  "shouldContinue": true
}
```

## 本地真实链路测试

当前已经验证：

- vivo text-only `chat/completions` 可以正常返回。
- `/analyze` 使用真实 runner 可以返回结构化 `tap` 结果。
- 当前模型不支持图片输入，传 `image_url` 会返回 `Model do not support image input`。

因此当前推荐：

```bash
export OPENAI_INCLUDE_SCREENSHOT=false
```

之后再启动真实 runner。

## 开发文件结构

当前推理面核心文件：

```text
src/agents/create-agent-runner.ts
src/agents/model-client.ts
src/agents/openai-compatible-agent-runner.ts
src/plugins/openai-compatible-client.ts
src/services/agent-context-builder.ts
src/services/agent-output-parser.ts
```

测试文件：

```text
src/__test__/agent-context-builder.test.ts
src/__test__/agent-output-parser.test.ts
src/__test__/create-agent-runner.test.ts
src/__test__/openai-compatible-agent-runner.test.ts
src/__test__/openai-compatible-client.test.ts
```

## 验证命令

开发后建议跑：

```bash
pnpm run typecheck
pnpm run lint
pnpm run format:check
pnpm run test
```

当前最近一次验证结果：

```text
typecheck passed
lint passed
format:check passed
test passed
10 test files, 32 tests
```

## 注意事项

- `buildApp()` 默认仍可注入任意 `AgentRunner`，方便 A 侧控制面和测试继续解耦。
- `main.ts` 启动时才根据 `AGENT_RUNNER` 选择真实 runner 或 mock runner。
- 当前不依赖数据库完成，`recentMessages` 为空时也能工作。
- 截图目前是否发送给模型由 `OPENAI_INCLUDE_SCREENSHOT` 控制。
- 如果后续换成支持图片的模型，再把 `OPENAI_INCLUDE_SCREENSHOT=true` 打开即可。
