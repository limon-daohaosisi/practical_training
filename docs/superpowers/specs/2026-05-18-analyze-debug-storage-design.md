# Analyze Debug Storage Design

**Goal**

收到 Android 端发来的 `POST /analyze` 请求后，server 在本地临时目录里创建一个独立会话目录，分别保存：
- `metadata.json`
- `screenshot.jpg`

并在响应里返回这两个文件的本地路径，方便联调时直接查看请求内容。

**Scope**

本次只做调试存档能力，不实现真实分析逻辑。

**Design**

1. 在 `server/src/services/` 新增一个纯 service：`analyze-debug-storage.ts`
2. service 负责：
- 在系统临时目录下创建根目录 `guide-assistant-analyze/`
- 每次请求创建一个子目录，目录名格式为 `<timestamp>-<random>`
- 将 metadata 以格式化 JSON 形式写入 `metadata.json`
- 将 screenshot 原始字节写入 `screenshot.jpg`
3. `routes/analyze/analyze.handler.ts` 保持薄：
- 检查 multipart/form-data
- 解析出 `metadata` 和 `screenshot`
- 调用 storage service 落盘
- 返回成功响应以及 `savedMetadataPath` / `savedScreenshotPath`
4. `routes/analyze/analyze.schema.ts` 更新调试响应 schema
5. 在 `server/src/__test__/` 补测试：
- multipart 请求成功时，返回 200
- 响应里包含本地路径
- 对应路径下确实存在 `metadata.json` 和 `screenshot.jpg`
- 缺少 `metadata` 或 `screenshot` 时仍返回 400

**Directory Layout**

示例：

`<tmp>/guide-assistant-analyze/20260518T153000-abc123/metadata.json`

`<tmp>/guide-assistant-analyze/20260518T153000-abc123/screenshot.jpg`

**Constraints**

- 不把文件写到仓库目录内
- 不引入数据库或对象存储
- 不在 route handler 中直接堆文件系统细节
- 文件命名固定，便于人工查找

**Testing**

- `server/src/__test__/analyze.contract.test.ts` 扩展为检查落盘结果
- 运行 `cd server && pnpm run test`
- 最终再运行 `cd server && pnpm run check`（如果环境允许）
