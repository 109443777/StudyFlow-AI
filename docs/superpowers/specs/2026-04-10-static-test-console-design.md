# StudyFlow 静态测试台设计文档

## 目标

在现有 Spring Boot 后端工程中增加一个单页静态前端，用来集中调用和展示 StudyFlow AI 已有的后端接口能力，方便本地联调、功能演示和项目答辩，不新增独立前端工程。

## 范围

这是一个测试与演示页面，不是正式产品页面。

本轮纳入范围的能力：
- 系统健康检查
- 用户注册
- 用户登录
- 当前用户信息查询
- 普通资料上传
- 分片上传链路测试
- 资料详情查询
- 我的资料列表查询
- 解析任务查询
- 文本内容查询
- 摘要查询与手动生成
- 转写结果查询
- 问答会话创建
- 基于资料的提问与历史查询
- 复习提纲生成
- 考试计划生成
- 学习计划历史与详情查询
- 失败任务记录查询与补偿

本轮不做：
- 正式产品级 UI
- Vue 或独立前端工程
- OCR 和媒体预览
- 图表分析面板
- Swagger 全接口浏览器

## 架构设计

前端页面直接由 Spring Boot 从 `src/main/resources/static` 提供，使用原生 HTML、CSS 和 JavaScript，通过 `fetch` 调用后端接口。登录态保存在 `localStorage`，由统一请求封装自动注入 `Authorization` 头。

为了保持代码可维护性，但又不引入框架复杂度，页面拆成三个静态文件：
- `index.html`：页面结构
- `styles.css`：样式与布局
- `app.js`：请求逻辑、状态处理、事件绑定

## 页面结构

页面采用单页控制台形式，按功能分区展示。

区块包括：
1. 系统状态
2. 认证区
3. 资料上传区
4. 解析结果区
5. RAG 问答区
6. 学习辅助区
7. 失败任务区
8. 全局请求日志区

每个区块包含：
- 表单输入
- 操作按钮
- 结果展示面板

## 视觉方向

页面以“清晰可测”为主，不做复杂设计，但要避免过于粗糙。

样式原则：
- 使用偏暖的浅色背景，不用纯白默认页
- 区块采用卡片式布局
- 请求日志区使用等宽字体
- 状态信息通过标签色区分
- 桌面端双列或网格布局，移动端自动堆叠

## 数据流说明

### 认证流程

- 注册：`POST /api/auth/register`
- 登录：`POST /api/auth/login`
- 登录成功后将 JWT 保存到 `localStorage`
- 当前用户信息：`GET /api/users/me`

### 资料流程

- 普通上传：`POST /api/materials/upload`
- 分片上传：
  - `POST /api/material-uploads/init`
  - `POST /api/material-uploads/chunk`
  - `GET /api/material-uploads/{uploadId}/chunks`
  - `POST /api/material-uploads/complete`
- 资料查询：
  - `GET /api/materials/{materialId}`
  - `GET /api/materials/my`

### 解析与内容流程

- 解析任务查询：
  - `GET /api/parse-tasks/{taskId}`
  - `GET /api/parse-tasks`
- 文本内容查询：
  - `GET /api/material-contents?materialId=...`
- 摘要查询与生成：
  - `GET /api/material-summaries?materialId=...`
  - `POST /api/material-summaries/{materialId}/generate`
- 转写查询：
  - `GET /api/media-transcripts?materialId=...`

### 问答流程

- 创建问答会话：`POST /api/qa/sessions`
- 提问：`POST /api/qa/sessions/{sessionId}/ask`
- 历史记录：`GET /api/qa/sessions/{sessionId}/messages`

### 学习辅助流程

- 生成复习提纲：`POST /api/study-plans/review-outline`
- 生成考试计划：`POST /api/study-plans/exam-plan`
- 学习计划详情：`GET /api/study-plans/{planId}`
- 学习计划历史：`GET /api/study-plans`

### 治理与补偿流程

- 失败任务查询：`GET /api/task-failures`
- 手动补偿：`POST /api/task-failures/{recordId}/compensate`

## JavaScript 设计

`app.js` 采用轻量分层，不把全部逻辑塞进一个超大函数。

内部结构规划：
- 接口地址常量
- DOM 读写工具
- token 存取工具
- 通用 `apiRequest()` 请求封装
- 每个功能区独立的事件处理函数
- `DOMContentLoaded` 时统一绑定

共享状态仅保留最少内容：
- 当前 JWT
- 最近一次操作得到的 `materialId`
- 最近一次操作得到的 `uploadId`
- 最近一次操作得到的 `sessionId`

## 异常处理

所有请求统一走通用请求方法，负责：
- 自动附带 token
- 尝试解析 JSON
- 提取后端 `Result` 的 `code / message / data`
- 将成功和失败记录写入页面日志区

页面层处理原则：
- 每个功能区都保留最近一次请求结果
- 请求失败时不清空结果
- 对未登录访问给出明确提示

## 测试策略

由于这是静态资源页面，本轮测试重点是：
- 后端回归测试：运行 `backend/mvnw.cmd test`
- 静态资源可访问验证：确认 Spring Boot 能成功提供页面和脚本
- 手工冒烟测试：
  - 打开首页
  - 注册并登录
  - 调用健康检查
  - 上传一个文本资料
  - 查询资料列表
  - 创建问答会话并提问
  - 生成一次复习提纲

## 文件规划

新增文件：
- `backend/src/main/resources/static/index.html`
- `backend/src/main/resources/static/styles.css`
- `backend/src/main/resources/static/app.js`

更新文件：
- `backend/README.md`

## 风险与应对

### 风险 1：单页内容过多，界面拥挤

应对：
- 分功能卡片展示
- 表单字段尽量精简
- 结果展示统一使用格式化 JSON 面板

### 风险 2：受保护接口失败时不易判断原因

应对：
- 认证区显示当前 token 状态
- 日志区记录请求路径、方法、结果和错误信息

### 风险 3：分片上传在原生 JS 下操作繁琐

应对：
- 本轮只做测试型交互
- 支持手动走完整分片链路，不追求产品级上传体验

## 验收标准

满足以下条件即视为完成：
- 后端能成功提供单页静态测试台
- 页面可完成登录并持久化 JWT
- 页面能从单一入口覆盖当前主要后端能力
- 不打开浏览器开发者工具也能看到请求结果
- 后端测试保持通过
