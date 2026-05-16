# Analyze 接口第一版合同 RFC

## 1. 文档目标

本 RFC 用于确定 Android 客户端与 Server 之间第一版 `analyze` 接口的外部合同范围。

本文件解决的问题只有一个：

在 MVP 第一阶段，Android 端发什么，Server 端回什么，哪些字段现在必须定，哪些字段现在不要提前设计。

本 RFC 的结论应落地到：

- `contracts/openapi/analyze.yaml`

## 2. 背景

当前项目已经完成仓库初始化、Server 基础骨架、校验体系和 CI。

下一步要进入真实开发阶段，Android 与 Server 必须先共享一份最小可联调的外部协议，否则会出现：

1. Android 端不知道该发哪些字段
2. Server 端不知道该保证哪些返回字段
3. 双方各自写一套结构，后续联调返工

因此，本 RFC 只定义当前主链路所必需的字段，不追求一次性设计未来所有接口。

## 3. 范围

本 RFC 只覆盖：

- `POST /analyze` 的请求结构
- `POST /analyze` 的成功响应结构
- `POST /analyze` 的错误响应结构
- 第一版必须遵守的行为规则

本 RFC 不覆盖：

- 未来多轮会话接口
- 历史记录接口
- 远程协助接口
- 异步任务接口
- 第二阶段实时语音链路

## 4. 设计原则

第一版合同遵守以下原则：

1. 只定义当前主链路必须字段
2. 外部合同只保留一份真源
3. Android 与 Server 共享的是外部协议，不是内部实现结构
4. 先保证可联调，再逐步扩展
5. 对“不确定”场景做显式约束，避免模型瞎猜

## 5. 当前必须先定的接口

当前阶段只要求正式定义以下接口：

1. `POST /analyze`
2. `GET /health` 可作为极简可选补充

其中核心是：

1. `POST /analyze`

## 6. `POST /analyze` 请求字段

第一版请求体建议先使用：

- `multipart/form-data`

这样做的原因是：

1. 截图属于文件上传场景
2. 二进制上传比 Base64 更节省体积
3. 后续更容易扩展到对象存储或文件引用方案

第一版请求体分为两部分：

1. `metadata`
2. `screenshot`

### 6.1 顶层请求字段

第一版 `multipart/form-data` 请求必须包含以下 part：

1. `metadata`
2. `screenshot`

### 6.2 字段说明

#### `metadata`

- 类型：`application/json`
- 是否必填：是
- 含义：结构化请求元数据

第一版 `metadata` 现在必须包含以下字段：

1. `question`
2. `packageName`
3. `activityName`
4. `screenWidth`
5. `screenHeight`
6. `imageWidth`
7. `imageHeight`
8. `nodes`

#### `screenshot`

- 类型：二进制文件
- 推荐 MIME 类型：`image/jpeg`
- 是否必填：是
- 含义：当前页面截图文件

#### `question`

- 类型：`string`
- 是否必填：是
- 含义：用户当前提出的问题

#### `packageName`

- 类型：`string`
- 是否必填：是
- 含义：当前页面所属 Android 包名

#### `activityName`

- 类型：`string`
- 是否必填：否
- 含义：当前窗口或 Activity 名称

#### `screenWidth`

- 类型：`integer`
- 是否必填：是
- 含义：当前屏幕宽度，单位为像素

#### `screenHeight`

- 类型：`integer`
- 是否必填：是
- 含义：当前屏幕高度，单位为像素

#### `imageWidth`

- 类型：`integer`
- 是否必填：是
- 含义：上传截图的实际图像宽度，单位为像素
- 备注：即使截图未缩放，也必须传该字段

#### `imageHeight`

- 类型：`integer`
- 是否必填：是
- 含义：上传截图的实际图像高度，单位为像素
- 备注：即使截图未缩放，也必须传该字段

#### `nodes`

- 类型：`array`
- 是否必填：是
- 含义：标准化后的关键节点数组
- 允许为空数组：是
- 顺序要求：按屏幕阅读顺序排序，优先按 `top` 从小到大，再按 `left` 从小到大

### 6.3 坐标统一规则

第一版必须明确区分以下两套坐标空间：

1. 屏幕坐标空间
2. 上传图像坐标空间

其中：

- `screenWidth` / `screenHeight` 定义屏幕坐标空间
- `imageWidth` / `imageHeight` 定义上传图像坐标空间

第一版必须遵守以下规则：

1. `nodes.bounds` 一律使用屏幕坐标空间
2. Server 最终返回的 `target.bounds` 一律使用屏幕坐标空间
3. 上传给 Server 的截图允许与屏幕尺寸不同
4. 如果截图经过缩放，Server 必须负责将图像坐标换算回屏幕坐标后再返回
5. Android 客户端收到 `target.bounds` 后，应直接按屏幕坐标绘制高亮，不再自行猜测缩放比例

补充说明：

- 如果截图没有缩放，则通常 `imageWidth = screenWidth`、`imageHeight = screenHeight`
- 如果截图已经缩放，则 `imageWidth` / `imageHeight` 与 `screenWidth` / `screenHeight` 可以不同
- 第一版不要求客户端单独上传“图像坐标点”，只要求明确图像尺寸

## 7. `nodes` 第一版子字段

第一版每个标准化节点对象至少要包含以下字段：

1. `text`
2. `contentDescription`
3. `className`
4. `clickable`
5. `enabled`
6. `bounds`

这里的含义不是“Android 原始无障碍节点天然就完整具备这些值”，而是：

Android 客户端在读取原始 `AccessibilityNodeInfo` 后，需要将其归一化为统一的节点对象结构，再上传给 Server。

### 7.1 字段说明

#### `text`

- 类型：`string`
- 是否必须包含该 key：是
- 含义：节点自身可见文本
- 备注：如果原始节点没有文本，统一写为空字符串

#### `contentDescription`

- 类型：`string`
- 是否必须包含该 key：是
- 含义：节点的无障碍描述文本
- 备注：如果原始节点没有描述，统一写为空字符串

#### `className`

- 类型：`string`
- 是否必须包含该 key：是
- 含义：节点对应的 Android 类名或控件类型信息
- 备注：如果原始节点没有可用类名，统一写为空字符串

#### `clickable`

- 类型：`boolean`
- 是否必须包含该 key：是
- 含义：该节点是否可点击

#### `enabled`

- 类型：`boolean`
- 是否必须包含该 key：是
- 含义：该节点当前是否可用

#### `bounds`

- 类型：`object`
- 是否必须包含该 key：是
- 含义：节点在屏幕坐标系中的矩形区域

### 7.2 `bounds` 子字段

`bounds` 必须包含：

1. `left`
2. `top`
3. `right`
4. `bottom`

### 7.3 节点字段要求

- `text` 可以为空字符串
- `contentDescription` 可以为空字符串
- `className` 可以为空字符串
- `clickable` 必须存在
- `enabled` 必须存在
- `bounds` 必须存在

## 8. `POST /analyze` 成功响应字段

第一版成功响应现在必须定以下字段：

1. `answer`
2. `target`
3. `action`

### 8.1 `answer`

- 类型：`string`
- 是否必填：是
- 含义：给用户展示和播报的主回答

### 8.2 `target`

- 类型：`object | null`
- 是否必填：是
- 含义：当前建议操作的目标区域

如果无法可靠定位，必须允许返回 `null`。

`target` 第一版至少包含：

1. `label`
2. `bounds`

#### `target.label`

- 类型：`string`
- 含义：目标描述，例如“发送按钮”

#### `target.bounds`

- 类型：`object`
- 含义：目标高亮区域坐标
- 坐标系：客户端当前屏幕坐标系
- 单位：像素
- 约束：必须与请求中的 `screenWidth` 和 `screenHeight` 对齐

如果上传给 Server 的截图经过压缩或缩放，则 Server 应基于图像尺寸与屏幕尺寸的映射关系，将模型理解到的目标区域换算回当前屏幕坐标系后再返回。

子字段必须包含：

1. `left`
2. `top`
3. `right`
4. `bottom`

### 8.3 `action`

- 类型：`object`
- 是否必填：是
- 含义：建议动作

第一版至少包含：

1. `type`

#### `action.type`

- 类型：`string`
- 枚举值：
  - `tap`
  - `scroll`
  - `none`

### 8.4 `target` 与 `action` 的关系约束

第一版必须满足以下规则：

1. 当 `action.type = tap` 时，`target` 必须非空
2. 当 `action.type = scroll` 时，`target` 可以为空
3. 当 `action.type = none` 时，`target` 必须为 `null`

## 9. 错误响应字段

第一版错误响应至少统一为以下结构：

1. `code`
2. `message`

### 9.1 推荐错误码

第一版建议至少定义：

1. `INVALID_REQUEST`
2. `ANALYSIS_FAILED`
3. `INTERNAL_ERROR`

### 9.2 推荐 HTTP 状态码映射

第一版建议按以下方式映射：

1. `INVALID_REQUEST` -> `400`
2. `ANALYSIS_FAILED` -> `502`
3. `INTERNAL_ERROR` -> `500`

补充约定：

- “无法可靠定位目标”不属于错误响应
- 遇到这类情况时，应返回 `200`
- 同时返回：
  - `target = null`
  - `action.type = none`

## 10. 第一版必须遵守的行为规则

这部分不是字段本身，但必须进入合同说明或配套实现约定。

### 10.1 不确定时禁止硬返回目标

如果模型无法可靠定位目标：

- `target` 必须返回 `null`

### 10.2 不安全时禁止硬给动作

如果当前无法安全给出操作建议：

- `action.type` 必须返回 `none`

### 10.3 不允许为了完整性伪造坐标

如果无法确定真实坐标：

- 不允许返回猜测坐标

## 11. 现在明确不进入第一版合同的字段

以下字段暂不进入第一版正式合同：

1. `sessionId`
2. `requestId`
3. 多轮历史上下文
4. `ocrBlocks` 明细
5. 完整原始节点树透传结构
6. 风险标签数组
7. prompt 版本
8. 模型中间推理信息
9. Agent 内部执行轨迹

这些字段后续如果需要，应通过新的 RFC 或合同变更再加入。

## 12. 实施顺序

按以下顺序推进：

1. 先将本 RFC 的字段落到 `contracts/openapi/analyze.yaml`
2. Android 端按该合同组织请求结构
3. Server 端按该合同组织响应结构
4. 使用 mock 数据先完成 Android 与 Server 解耦联调
5. 联调后根据真实问题再增量扩展合同

## 13. 本 RFC 的结论

第一版 `analyze` 合同现在只需要定清楚：

1. 用户问了什么
2. 当前页面是谁
3. 当前屏幕是什么样
4. 当前关键节点有哪些
5. Server 返回什么引导结果
6. 不确定时必须怎么返回

这已经足够支撑当前 MVP 主链路开发，不需要一次性提前设计后续所有 API。
