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
export OPENAI_MODEL="Doubao-Seed-2.0-mini"
export OPENAI_INCLUDE_SCREENSHOT=true
```

说明：

- `OPENAI_API_KEY` 填 vivo 文档里的 `AppKey`。
- `OPENAI_BASE_URL` 建议填 `https://api-ai.vivo.com.cn/v1`。
- `OPENAI_MODEL` 填账号有权限的模型名。需要图片输入时，当前实测建议使用 `Doubao-Seed-2.0-mini`、`Doubao-Seed-2.0-lite`、`Doubao-Seed-2.0-pro` 或 `qwen3.5-plus`。
- `OPENAI_INCLUDE_SCREENSHOT=true` 或不设置该变量时，会把截图作为 base64 `image_url` 传给模型。
- `OPENAI_INCLUDE_SCREENSHOT=false` 表示只把节点树和上下文发给模型，适合文本-only 模型。

注意：是否真正能识别图片，最终取决于 `OPENAI_MODEL` 和 AppKey 权限。如果模型不支持视觉输入，vivo 会返回类似 `Model do not support image input` 的错误。当前本地 AppKey 实测下，`Volc-DeepSeek-V3.2` 会返回该错误，不适合作为图片链路测试模型。

建议本地创建不提交的 `server/.env.local`：

```bash
export OPENAI_API_KEY="你的 vivo AppKey"
export OPENAI_BASE_URL="https://api-ai.vivo.com.cn/v1"
export OPENAI_MODEL="Doubao-Seed-2.0-mini"
export OPENAI_INCLUDE_SCREENSHOT=true
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

如果当前模型不支持图片输入，可以显式关闭截图输入：

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
- 默认会把本次截图作为 OpenAI vision 兼容的 `image_url` content part 传给模型。
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

## 图片输入模型选择

vivo 官方文档页把文本生成和图片理解示例放在同一个 `chat/completions` 页面，但不同模型的图片输入能力并不完全一致。当前使用本地 AppKey 和 `test/a4dc7eec736a97fad1a6152c1d22bdb8.jpg` 按官方 `image_url` 格式实测结果如下：

| 模型 | 图片输入结果 | 备注 |
| --- | --- | --- |
| `Volc-DeepSeek-V3.2` | 失败 | 返回 `1010 Model do not support image input` |
| `Doubao-Seed-2.0-mini` | 成功 | 推荐用于第一版真实图片链路测试 |
| `Doubao-Seed-2.0-lite` | 成功 | 可用于图片链路测试 |
| `Doubao-Seed-2.0-pro` | 成功 | 可用于图片链路测试 |
| `qwen3.5-plus` | 成功 | 可用于图片链路测试 |

因此，如果要测试截图理解，请优先配置：

```bash
export OPENAI_MODEL="Doubao-Seed-2.0-mini"
export OPENAI_INCLUDE_SCREENSHOT=true
```

如果必须使用 `Volc-DeepSeek-V3.2`，建议关闭截图：

```bash
export OPENAI_INCLUDE_SCREENSHOT=false
```

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
- 如果模型支持图片输入，runner 会按 OpenAI vision 兼容格式传入截图。
- 如果模型不支持图片输入，传 `image_url` 可能返回 `1010 Model do not support image input`。
- 当前 server Fastify `bodyLimit` 为 10MB，可接收常见截图上传。
- 使用 `test/a4dc7eec736a97fad1a6152c1d22bdb8.jpg` 构造真实图片请求时，请求已成功通过 server 上传限制并发送到 vivo。实测 `Doubao-Seed-2.0-mini`、`Doubao-Seed-2.0-lite`、`Doubao-Seed-2.0-pro` 和 `qwen3.5-plus` 可以识别图片，`Volc-DeepSeek-V3.2` 返回 `1010 Model do not support image input`。

文本-only 模型可以使用：

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
10 test files, 34 tests
```

## 注意事项

- `buildApp()` 默认仍可注入任意 `AgentRunner`，方便 A 侧控制面和测试继续解耦。
- `main.ts` 启动时才根据 `AGENT_RUNNER` 选择真实 runner 或 mock runner。
- 当前不依赖数据库完成，`recentMessages` 为空时也能工作。
- 截图目前是否发送给模型由 `OPENAI_INCLUDE_SCREENSHOT` 控制。
- 默认会发送截图；如果后续换成文本-only 模型，再把 `OPENAI_INCLUDE_SCREENSHOT=false` 关闭即可。
