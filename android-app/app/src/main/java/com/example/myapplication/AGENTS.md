# AGENTS.md

## 适用范围

本文件适用于：

- `android-app/app/src/main/java/com/example/myapplication/`

## 目录目标

这里存放 Android 客户端的应用源码。

当前 Android 端采用：

- 单 `app` 模块
- 包结构按 `app + feature + core` 分层

目标是：

1. 保持页面功能和系统能力分离
2. 保持 Android UI 与跨 App 能力分离
3. 为后续无障碍、截图、overlay、网络联调保留清晰边界

## 当前包结构

推荐结构如下：

- `app/`
- `feature/`
  - `onboarding/`
  - `session/`
  - `settings/`
- `core/`
  - `accessibility/`
  - `capture/`
  - `overlay/`
  - `speech/`
  - `network/`
  - `model/`
  - `session/`
  - `util/`

## 分层职责

### `app/`

放应用入口与应用级装配代码。

这里适合放：

- `MainActivity`
- `Application`
- 全局导航入口
- 应用级初始化

不要在这里堆积具体业务逻辑。

### `feature/`

放 Android 客户端自己的页面功能流。

当前建议按功能拆：

- `onboarding/`：权限引导、首次使用流程
- `session/`：会话控制页、当前引导状态页
- `settings/`：配置与调试设置

这里主要是：

- Compose UI
- ViewModel
- 页面状态

不要在 `feature/` 中直接堆积无障碍、截图、overlay 的底层实现。

### `core/`

放 Android 客户端的系统能力和通用能力。

#### `core/accessibility/`

负责：

- `AccessibilityService`
- 当前窗口监听
- 节点树采集
- 节点标准化

这是 Android 端核心能力之一。

#### `core/capture/`

负责：

- 截图获取
- 图像压缩
- 图像尺寸记录
- 屏幕坐标与图像坐标映射

#### `core/overlay/`

负责：

- overlay 生命周期
- 高亮框绘制
- 气泡提示
- 箭头等辅助展示

#### `core/speech/`

负责：

- 语音识别
- TTS 播报

#### `core/network/`

负责：

- Android <-> Server 请求发送
- multipart 上传
- DTO / API 定义
- 响应解析

外部协议必须以 `contracts/openapi/` 为准。

#### `core/model/`

负责跨 Android 包共享的数据模型。

例如：

- `Bounds`
- `UiNode`
- `AnalyzeResult`

不要把网络 DTO 和领域模型长期混在一起；如果后续复杂度上升，应逐步分离。

#### `core/session/`

负责客户端内部会话状态。

例如：

- 当前页面上下文
- 最近一次请求
- 最近一次响应
- 当前引导状态

#### `core/util/`

只放小型、通用、无状态工具函数。

不要把业务逻辑塞进 `util/`。

## 真正的核心工作在哪里

这个项目的 Android 端真正核心工作，不在普通页面层，而主要在：

1. `core/accessibility/`
2. `core/capture/`
3. `core/overlay/`
4. `core/network/`

`feature/` 更像是控制面板和用户入口。

## 与 Server / Contracts 的关系

Android 端属于客户端，不是外部协议真源。

必须遵守：

- 外部 HTTP 合同以 `contracts/openapi/` 为准
- 不要在 Android 端私自发明另一套字段名或字段语义
- 如果 multipart、节点结构、坐标规则变化，应先看 RFC 和 `contracts/`

## 修改原则

修改 Android 客户端代码时，优先保证：

1. 页面层与系统能力层边界清晰
2. 无障碍、截图、overlay、网络逻辑不要混写在同一处
3. 坐标规则统一以屏幕坐标为最终真源
4. 如果功能还没打通，优先做最小链路验证，不要提前堆太多页面细节

## 校验与格式化

Android 端当前默认使用以下检查命令：

```bash
cd android-app
./gradlew ktlintCheck
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew androidCheck
```

说明：

- `ktlintCheck`：Kotlin 格式检查
- `ktlintFormat`：Kotlin 自动格式化
- `lintDebug`：Android Lint
- `testDebugUnitTest`：Android 单元测试
- `assembleDebug`：Debug 构建
- `androidCheck`：当前 Android 端聚合校验入口

默认规则：

1. Android 端改动后，优先运行 `./gradlew androidCheck`
2. 如果执行了复杂任务或连续修改多个 Android 文件，交接前应运行 `./gradlew ktlintFormat`
3. 如果当前环境无法完成 Gradle 校验，必须明确说明原因，例如：
   - JDK 未配置
   - Gradle 下载失败
   - Android SDK 或网络环境问题

## 审查清单

改动 Android 代码后，至少自查：

1. 这段代码应该放在 `feature/` 还是 `core/`
2. 是否把系统能力代码写进了页面层
3. 是否与 `contracts/openapi/` 中的字段语义一致
4. 是否引入了不必要的提前抽象
5. 是否影响后续 Android 与 Server 联调
