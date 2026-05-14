# Android AI 应用引导助手项目规划书

## 1. 项目目标

做一款 Android 辅助型 App，帮助用户，尤其是不熟悉智能手机和新应用的老人，理解当前屏幕上的内容，并通过语音和屏幕高亮的方式指导下一步操作。

核心能力：

1. 用户打开任意第三方 App 后，本产品可感知当前界面内容。
2. 用户通过语音提问，例如“现在我要点哪里”“怎么发消息”“这个页面下一步怎么走”。
3. 系统结合当前界面截图、无障碍节点树、上下文历史，调用模型分析。
4. 系统返回：
   - 下一步操作建议
   - 目标控件描述
   - 目标区域坐标
   - 风险提示
5. 本产品通过悬浮高亮、箭头、框选、语音播报，指导用户完成操作。

目标不是“自动替用户操作一切”，而是“实时、低门槛、可信的交互式引导”。

---

## 2. 产品定义

### 2.1 目标用户

1. 老年用户
2. 第一次使用某个新 App 的普通用户
3. 视力弱、阅读慢、界面理解成本高的用户
4. 远程协助场景中的被指导方

### 2.2 核心价值

1. 把“不会用 App”变成“边看边学”
2. 降低复杂界面理解成本
3. 降低用户求助家人或客服的频率
4. 提供比图文教程更实时、更上下文相关的帮助

### 2.3 MVP 成功标准

MVP 阶段只验证一个命题：

“用户在陌生 App 中发起提问后，系统是否能在 2 到 5 秒内，给出可执行、可理解、可定位的下一步指引。”

建议用以下指标判断：

1. 单轮指引成功率 > 70%
2. 坐标定位命中率 > 85%
3. 单次推理端到端耗时 < 5 秒
4. 用户能理解语音提示且能按提示完成操作
5. 连续 10 分钟使用不过热、不明显卡顿

---

## 3. 先讲清楚的现实约束

这个项目可做，但必须从一开始按 Android 平台规则设计，不能按“后台偷偷看屏幕和录音”的思路做。

### 3.1 权限与能力边界

Android 上真正稳定、可量产的路线有两类：

1. `AccessibilityService`
   - 能拿到当前窗口的无障碍节点树
   - 能监听窗口变化、焦点变化、文本变化
   - Android 新版本支持 `takeScreenshot()`
   - 能创建 `TYPE_ACCESSIBILITY_OVERLAY` 覆盖层
   - 在合规前提下，最适合做“跨 App 辅助引导”

2. `MediaProjection`
   - 能做屏幕采集
   - 必须由用户明确授权
   - 适合连续截图、录屏、远程协助
   - 工程复杂度和权限敏感度更高

MVP 不建议一开始走“持续 MediaProjection + 后台实时分析”。更合理的方案是：

1. 以 `AccessibilityService` 为主
2. 在用户发问时按需截图
3. 优先结合节点树和截图做理解
4. 只在后续版本再引入连续屏幕流

### 3.2 录音限制

1. 录用户麦克风是可行的，但需要 `RECORD_AUDIO`
2. 长时间后台录音在体验和合规上都敏感，建议只在“辅助会话”中开启
3. 录第三方 App 内部声音不能当成默认能力，Android 对播放捕获有限制，且取决于对方 App 是否允许

因此，MVP 的语音输入只做“用户对着手机说话”，不做“自动监听所有外放声音并理解”

### 3.3 覆盖层限制

跨 App 高亮可以做，但要区分路线：

1. 如果主能力建立在无障碍服务上，优先用 `TYPE_ACCESSIBILITY_OVERLAY`
2. 不建议 MVP 依赖普通悬浮窗 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`

原因：

1. 无障碍覆盖层更契合产品定位
2. 权限和场景一致性更高
3. 对目标控件做高亮和说明更直接

### 3.4 上架合规风险

这类产品最大的风险不是技术，而是 Google Play 审核：

1. 无障碍权限必须服务于真实辅助功能场景
2. 不能包装成“偷偷监控用户一切”
3. 采集屏幕、录音、上传截图必须有明确披露与用户同意
4. 涉及支付、转账、密码、验证码等高风险页面时，需要明确的限制策略

结论：

产品定义必须是“辅助使用与可访问性工具”，不是“跨应用监控助手”。

---

## 4. 技术路线总建议

## 4.1 总体判断

你的偏好是复用成熟库、简化流程。基于这个目标，我建议：

### Android 端

1. 原生 Android
2. 开发语言：`Kotlin`
3. UI：`Jetpack Compose`
4. 核心跨 App 能力：`AccessibilityService`
5. 屏幕理解数据：`AccessibilityNodeInfo` + `AccessibilityService.takeScreenshot()`
6. 本地 OCR：`Google ML Kit Text Recognition`
7. 语音输入：
   - MVP：Android 原生 `SpeechRecognizer`
   - 稳定版：服务端 ASR 或 OpenAI Realtime/音频转写
8. 语音播报：Android 原生 `TextToSpeech`
9. 覆盖层高亮：`TYPE_ACCESSIBILITY_OVERLAY`
10. 网络：`Retrofit + OkHttp + Kotlin Serialization`
11. 本地存储：`Room + DataStore`
12. 依赖注入：`Hilt`

### Agent / 服务端

1. 服务端语言：`TypeScript`
2. 运行时：`Node.js`
3. Web 框架：`Fastify`
4. OpenAI 接入：
   - MVP：`Responses API`
   - 输出约束：`Structured Outputs / JSON Schema`
5. Agent 编排：
   - MVP：先不用 LangChain/LangGraph
   - 第二阶段：如需多工具、多步推理、trace，再考虑 `@openai/agents`
6. 图像理解：直接使用支持图像输入的模型
7. 观测：`OpenTelemetry` 或最小版结构化日志
8. 缓存/会话：`Redis` 可选，MVP 可先不上

这套组合的核心优点：

1. Android 端是最主流、资料最多、长期维护成本最低的方案
2. 服务端避免引入过重 agent 框架，先把“可靠输出结构”做好
3. 后续可以平滑升级到更复杂的 agent orchestration

---

## 5. 为什么不建议一开始用跨平台

虽然 Flutter/React Native 能更快做普通 App UI，但你这个产品的核心难点不在普通页面，而在系统级能力：

1. `AccessibilityService`
2. 覆盖层
3. 截图
4. 前台服务
5. 权限流转
6. 音频录制与播报
7. 多厂商 ROM 兼容

这些能力最终都会回到原生层。若用 Flutter 或 React Native，通常会变成：

1. UI 是跨平台
2. 所有关键能力都要自己写 Android 插件桥接
3. 排查系统问题更难
4. 真正省下来的时间不多

结论：

这个项目建议直接原生 Android，别在底层能力上绕路。

---

## 6. 推荐架构

### 6.1 架构分层

建议拆成 3 层：

1. Android 客户端
   - 权限申请
   - 无障碍服务
   - 截图与节点采集
   - 本地 OCR
   - 语音输入与播报
   - 覆盖层渲染
   - 会话状态管理

2. API 服务端
   - 接收截图、节点树、OCR 文本、用户问题
   - 统一构造模型输入
   - 调用 OpenAI 模型
   - 输出结构化指令
   - 风险控制与审计

3. 运维与观测层
   - 日志
   - 错误追踪
   - 调用耗时统计
   - 提示词版本管理

### 6.2 数据流

单轮问答流程：

1. 用户在第三方 App 中唤起辅助助手
2. `AccessibilityService` 获取当前活动窗口节点树
3. 同时调用截图接口获取当前屏幕图像
4. 客户端在本地提取：
   - 当前包名
   - Activity/窗口信息
   - 节点树摘要
   - 可点击节点列表
   - OCR 文本
   - 屏幕尺寸、像素比例
5. 用户说出问题，客户端得到文本
6. 客户端把截图和结构化 UI 数据发送到服务端
7. 服务端调用模型分析
8. 模型返回 JSON：
   - intent
   - explanation
   - target
   - boundingBox
   - confidence
   - caution
   - nextAction
9. 客户端将坐标映射到当前屏幕
10. 覆盖层高亮目标区域
11. TTS 播报提示

---

## 7. Android 端技术选型

## 7.1 开发语言与框架

### 结论

1. `Kotlin`
2. `Jetpack Compose`
3. 最低支持建议：`Android 10+`
4. 目标优化：`Android 13/14/15`

### 原因

1. Kotlin 是 Android 主流
2. Compose 适合快速构建设置页、权限引导页、会话页
3. Accessibility、Service、权限等系统能力与 Kotlin 集成最佳
4. Android 10+ 能减少过旧系统兼容坑

### 不建议

1. Java 新项目
2. Flutter 作为主框架
3. React Native 作为主框架

---

## 7.2 核心系统能力

### A. AccessibilityService

这是 MVP 的第一核心。

负责：

1. 监听当前窗口变化
2. 读取控件树
3. 找到可点击元素
4. 获取文本、contentDescription、bounds
5. 建立界面语义理解基础
6. 提供截图能力
7. 渲染辅助覆盖层

建议设计两个模块：

1. `AccessibilityCaptureManager`
2. `AccessibilityOverlayManager`

### B. 截图方案

MVP 优先级：

1. 首选：`AccessibilityService.takeScreenshot()`
2. 二选一兜底：`MediaProjection`

原因：

1. `takeScreenshot()` 与无障碍链路天然一致
2. 不需要先上完整录屏管线
3. 更适合“按需理解当前页面”

何时引入 `MediaProjection`：

1. 你要做连续流式分析
2. 你要做远程协助/录屏回放
3. 你要做更频繁的帧采样

### C. 覆盖层

MVP 直接使用无障碍覆盖层实现：

1. 高亮框
2. 半透明遮罩
3. 箭头
4. 气泡提示
5. “下一步点这里”语音同步提示

### D. 语音能力

输入：

1. MVP：`SpeechRecognizer`
2. 第二阶段：服务端语音识别或 Realtime

输出：

1. `TextToSpeech`

这么选是因为：

1. 原生 TTS 已够用
2. MVP 不需要先为语音栈付出额外复杂度
3. 重点应放在“界面理解与定位准确率”

---

## 7.3 本地 OCR

推荐：`Google ML Kit Text Recognition`

作用：

1. 补足无障碍节点树中缺失的文本
2. 处理一些纯图片页面、Canvas 页面、游戏化页面
3. 给模型补充视觉文本信号

为什么不用完全依赖模型 OCR：

1. 本地 OCR 便宜且快
2. 可减少上传图片后的识别负担
3. 能先在端上做一轮预筛选与结构化

---

## 7.4 Android 端推荐依赖

建议优先采用成熟、主流、低争议库：

1. UI：`Jetpack Compose`
2. 生命周期：`androidx.lifecycle`
3. DI：`Hilt`
4. 网络：`Retrofit`、`OkHttp`
5. JSON：`kotlinx.serialization`
6. 本地数据库：`Room`
7. KV：`DataStore`
8. 图片处理：`Coil` 可选
9. 并发：`Kotlin Coroutines`
10. OCR：`ML Kit`
11. 日志：`Timber` 或直接结构化日志封装

不建议上来就引入一堆架构库。

---

## 8. 服务端与 Agent 技术选型

## 8.1 服务端语言

推荐：`TypeScript + Node.js`

原因：

1. 与现代 API 工程配套成熟
2. OpenAI 官方 SDK 生态成熟
3. 团队后续补人相对容易
4. 适合快速迭代 prompt、schema、路由

备选：

1. Python

如果团队更偏 Python，也能做。但单从“工程简单”和“接口服务开发体验”看，我更倾向 TypeScript。

---

## 8.2 Web 框架

推荐：`Fastify`

原因：

1. 性能好
2. 比 Express 更现代
3. Schema、插件体系、类型体验更适合 AI API 服务
4. 比 NestJS 更轻

不建议 MVP 一开始就上 `NestJS`，除非团队已经非常熟悉。

---

## 8.3 OpenAI 集成方式

### MVP 结论

1. 用 `Responses API`
2. 用支持图像输入的模型做界面分析
3. 用 `Structured Outputs` 约束模型返回 JSON

原因：

1. 你的核心不是开放式聊天，而是“给我一个可靠的操作指令结构”
2. 比起引入复杂 agent 框架，先把输出结构稳定下来更重要
3. 模型输出只要能稳定落成 schema，客户端就能可靠画框和播报

### 建议输出 Schema

```json
{
  "screenSummary": "string",
  "userIntent": "string",
  "answer": "string",
  "target": {
    "label": "string",
    "reason": "string",
    "bounds": {
      "left": 0,
      "top": 0,
      "right": 0,
      "bottom": 0
    },
    "source": "accessibility|ocr|vision|merged"
  },
  "action": {
    "type": "tap|scroll|type|wait|confirm|none",
    "text": "string"
  },
  "caution": "string",
  "confidence": 0.0
}
```

### 服务端提示词设计原则

1. 优先参考无障碍节点树
2. 节点树不足时参考 OCR 与截图视觉内容
3. 如果定位不确定，必须返回不确定，不允许瞎猜
4. 高风险页面必须保守输出
5. 若涉及支付、转账、登录、验证码，优先提示用户自行确认

---

## 8.4 要不要一开始上 LangChain / LangGraph / AutoGen

我的建议：不要。

理由很直接：

1. 你的 MVP 本质上不是复杂多 agent 系统
2. 你现在最重要的是：
   - 拿到当前界面
   - 可靠抽取 UI 信息
   - 稳定返回目标坐标
3. 这类场景中，编排框架带来的收益远小于额外复杂度

### MVP 最优解

自己写一层薄封装：

1. `screen-analysis service`
2. `prompt builder`
3. `schema validator`
4. `risk guard`

### 第二阶段再考虑的 agent 框架

如果后续出现以下需求，再引入成熟 agent 库：

1. 多轮规划
2. 多工具调用
3. 自动检索产品帮助文档
4. 动态选择策略
5. 可视化 trace 和 handoff

优先考虑：

1. `@openai/agents`

不优先考虑：

1. LangChain
2. LangGraph

不是说不能用，而是你当前项目还没到必须依赖它们的复杂度。

---

## 8.5 Realtime 要不要上

### 结论

MVP 不建议一开始把 Realtime 作为主链路。

### 原因

Realtime 更适合：

1. 连续语音对话
2. 极低延迟语音交互
3. 边说边回

而你的当前主难点是：

1. 跨 App 上下文采集
2. 目标定位
3. 坐标映射
4. 风险控制

因此建议分阶段：

1. 第一阶段：客户端语音转文本后，请求服务端 `Responses API`
2. 第二阶段：如果你要做“像通话一样一直说”，再引入 `Realtime API`

这样最省事。

---

## 9. 推荐模型策略

这里不写死某一个长期固定模型名，原因是模型代际变化快。工程上应设计成“可配置模型路由”。

建议拆 3 类任务：

1. 主分析模型
   - 处理截图 + 节点树 + OCR + 用户问题
   - 要求多模态、推理稳定、结构化输出稳定

2. 轻量改写模型
   - 把模型分析结果改写成适合老人听懂的话
   - 可选更便宜的模型

3. 语音链路模型
   - 第二阶段才需要

MVP 可先只保留一个主分析模型，减少路由复杂度。

---

## 10. 服务端核心模块设计

建议服务端拆成以下模块：

### 10.1 `ingestion`

负责接收客户端请求：

1. 截图
2. 节点树
3. OCR 文本
4. 用户问题
5. 设备信息

### 10.2 `ui-normalizer`

负责把客户端上传的 UI 数据标准化：

1. 压缩节点树
2. 保留点击节点
3. 去重相似文案
4. 抽取屏幕主要区域
5. 标准化 bounds

### 10.3 `screen-analyzer`

负责构造模型输入并请求模型：

1. 系统提示词
2. 安全规则
3. 用户问题
4. 截图
5. 节点摘要
6. OCR 摘要

### 10.4 `response-validator`

负责：

1. JSON Schema 校验
2. bounds 合法性校验
3. confidence 范围校验
4. target 是否超出屏幕

### 10.5 `risk-guard`

负责检测高风险场景：

1. 支付
2. 转账
3. 删除
4. 授权
5. 修改密码
6. 验证码

如果命中：

1. 降低自动引导强度
2. 增加提示语
3. 必要时拒绝给出点击指令

### 10.6 `instruction-render-helper`

返回给客户端最终可消费的数据：

1. 标准框选坐标
2. 可播报文案
3. 操作摘要
4. 置信度

---

## 11. Android 客户端模块设计

建议最少拆这些模块：

### 11.1 `app`

普通 Compose 页面：

1. 首页
2. 权限引导页
3. 会话控制页
4. 历史记录页
5. 设置页

### 11.2 `accessibility-service`

负责：

1. 监听界面变化
2. 导出当前节点树
3. 调用截图
4. 管理覆盖层

### 11.3 `capture`

负责：

1. 截图转换
2. bitmap 压缩
3. 坐标基准处理

### 11.4 `ocr`

负责：

1. 本地 OCR
2. 结果归一化

### 11.5 `speech`

负责：

1. 语音识别
2. TTS 播报

### 11.6 `network`

负责调用后端 API

### 11.7 `overlay`

负责渲染：

1. 边框
2. 半透明遮罩
3. 文案气泡
4. 操作箭头

### 11.8 `session`

负责：

1. 当前 App 上下文
2. 当前轮问题
3. 最近若干轮引导历史

---

## 12. MVP 功能范围

MVP 建议收窄，不要一开始做得像通用操作系统助手。

### 12.1 必做

1. 权限引导
2. 无障碍服务开启流程
3. 用户在任意 App 中唤起辅助面板
4. 获取当前节点树
5. 获取当前截图
6. 本地 OCR
7. 用户语音提问
8. 请求服务端分析
9. 返回目标坐标与文案
10. 屏幕高亮和语音播报

### 12.2 不做

1. 自动代点
2. 自动填表
3. 长时间持续后台监听
4. 连续录屏分析
5. 远程协助
6. 多语言国际化
7. iOS 版本

---

## 13. 第二阶段功能

MVP 验证通过后再做：

1. 连续对话
2. Realtime 语音链路
3. 更好的视觉跟踪
4. 远程家属协助模式
5. 教学模式
6. 常见 App 专项提示词优化
7. 风险页面专项守护
8. 引导路径记忆

---

## 14. 安全与隐私设计

这是项目成败关键之一。

### 14.1 设计原则

1. 用户主动开启辅助会话
2. 明示采集哪些数据
3. 默认最小化上传
4. 敏感页面保守处理
5. 可删除历史
6. 传输全程 HTTPS
7. 服务端避免长期保存原始截图

### 14.2 建议默认策略

1. 默认只在用户提问时采集当前界面
2. 默认不连续上传屏幕流
3. 默认不保存原始语音
4. 默认对密码框、验证码框做脱敏
5. 对银行、支付、身份认证场景增加二次确认

### 14.3 风险页面策略

检测到以下场景时，模型不应直接强引导点击：

1. 支付确认
2. 银行转账
3. 删除账号
4. 身份验证
5. 密码修改
6. 验证码输入

应该转为：

1. 解释页面含义
2. 提醒用户自行核对
3. 降低自动化程度

---

## 15. 上架与合规建议

如果目标是 Google Play，上架策略要提前设计。

### 15.1 产品描述要点

必须明确这是：

1. 面向辅助使用和可访问性的操作引导工具
2. 帮助老人或不熟悉 App 的用户理解界面
3. 需要用户主动授权

### 15.2 要准备的材料

1. 权限用途说明
2. 应用内权限引导截图
3. 隐私政策
4. 数据收集说明
5. 无障碍功能说明视频

### 15.3 发布策略建议

建议顺序：

1. 先做企业内测或封闭测试
2. 再做邀请制测试
3. 最后评估是否公开上架

如果商业模式允许，也可以考虑：

1. 国内分发渠道先验证
2. 不把 Google Play 当第一落点

---

## 16. 开发阶段建议

## Phase 0：技术验证，1 到 2 周

目标：证明核心链路可通。

任务：

1. 做出 `AccessibilityService`
2. 成功拿到当前窗口节点树
3. 成功截图
4. 画出覆盖层高亮框
5. 把截图和节点树发到一个测试接口
6. 返回假数据并在屏幕上高亮

验收：

1. 能跨 2 到 3 个常见 App 正常工作
2. 坐标高亮无明显偏移

## Phase 1：MVP，3 到 6 周

目标：做出可演示版本。

任务：

1. 语音输入
2. OCR
3. 服务端模型分析
4. 结构化 JSON 输出
5. TTS 播报
6. 风险守护基础版

验收：

1. 支持微信、支付宝、系统设置等典型应用中的基础页面引导

## Phase 2：稳定性与体验，3 到 4 周

任务：

1. 提升截图、OCR、节点融合策略
2. 优化模型提示词
3. 优化高亮样式和语音播报节奏
4. 做日志与监控
5. 加入回放与问题定位工具

## Phase 3：增强版，按需要

任务：

1. 连续语音
2. Realtime
3. 家属远程辅助
4. App 专项优化策略

---

## 17. 团队配置建议

如果想快速做出 MVP，最低建议：

1. Android 工程师 1 名
2. 后端/AI 工程师 1 名
3. 产品/设计兼测试 1 名

如果只有 1 到 2 人，也能做，但要主动缩小范围。

---

## 18. 目录结构建议

### Android

```text
android-app/
  app/
  core/
  feature/
  service/
  overlay/
  speech/
  ocr/
  network/
```

### Server

```text
server/
  src/
    routes/
    services/
    prompts/
    schemas/
    guards/
    utils/
```

---

## 19. 我建议你直接采用的技术栈

如果你现在要一个可以直接开工、尽量少走弯路的版本，我建议就定这一套：

### Android

1. `Kotlin`
2. `Jetpack Compose`
3. `AccessibilityService`
4. `AccessibilityService.takeScreenshot()`
5. `TYPE_ACCESSIBILITY_OVERLAY`
6. `ML Kit Text Recognition`
7. `SpeechRecognizer`
8. `TextToSpeech`
9. `Retrofit + OkHttp`
10. `Room + DataStore`
11. `Hilt`

### Server

1. `Node.js`
2. `TypeScript`
3. `Fastify`
4. `OpenAI Responses API`
5. `Structured Outputs`
6. `Zod` 或 JSON Schema 做返回校验

### 明确不在 MVP 上的东西

1. Flutter
2. React Native
3. LangChain
4. LangGraph
5. AutoGen
6. 持续屏幕视频流
7. 自动代操作
8. 一开始就做 Realtime 全双工语音

---

## 20. 最终结论

这个项目的最佳起步方式不是“先搭一个复杂 agent 平台”，而是：

1. 先用 Android 原生把跨 App 感知、高亮和语音交互跑通
2. 先用 `AccessibilityService` 作为主能力，而不是一开始重押 `MediaProjection`
3. 先用 `Responses API + 结构化输出` 做一个稳定、可控的分析链路
4. 先验证“模型能不能可靠指出当前页面该点哪里”

如果这个命题被验证，再去上：

1. Realtime
2. 更复杂 agent 框架
3. 连续流式分析
4. 远程协助

这才是最省钱、最省时间、也最符合你“复用成熟库、简化流程”目标的路线。

---

## 21. 下一步建议

你接下来最值得做的不是继续讨论抽象方案，而是马上进入技术验证。

优先顺序：

1. 做 Android POC：无障碍服务 + 截图 + 高亮框
2. 定一版服务端 JSON Schema
3. 做一条完整链路：截图 -> 服务端分析 -> 返回坐标 -> 屏幕圈出 -> TTS 播报
4. 拿 2 到 3 个常见 App 页面做真机测试

如果你愿意，我下一步可以继续直接帮你产出两份文件：

1. `TECH_SPEC.md`
   - 更细到模块、类职责、接口字段、状态流
2. `MVP_TASKS.md`
   - 可直接开工的开发任务拆分，按周排期
