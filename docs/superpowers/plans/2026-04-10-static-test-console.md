# Static Test Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot 后端中增加一个单页静态测试台，用浏览器覆盖 StudyFlow AI 的主要后端接口能力。

**Architecture:** 页面由 Spring Boot 静态资源目录直接提供，使用原生 HTML、CSS、JavaScript 构建。通过统一 `fetch` 封装处理认证、日志和 `Result<T>` 响应，各功能区按卡片分组，避免引入单独前端工程和构建链。

**Tech Stack:** Spring Boot 3 静态资源、HTML5、CSS3、原生 JavaScript、MockMvc 测试、Maven

---

### Task 1: 为静态测试页建立可访问性测试

**Files:**
- Create: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`
- Test: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`

- [ ] **Step 1: 写失败测试，约束首页和静态脚本必须可访问**

```java
package com.studyflow.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StaticConsoleResourceTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeStaticIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("StudyFlow AI")));
    }

    @Test
    void shouldServeStaticAppScript() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("apiRequest")));
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=StaticConsoleResourceTests test
```

Expected:
- `404` 或资源不存在导致测试失败

- [ ] **Step 3: 先创建最小静态文件骨架让测试通过**

`backend/src/main/resources/static/index.html`

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>StudyFlow AI 测试台</title>
</head>
<body>
  <h1>StudyFlow AI</h1>
  <script src="/app.js"></script>
</body>
</html>
```

`backend/src/main/resources/static/app.js`

```javascript
async function apiRequest() {
  return null;
}
```

- [ ] **Step 4: 重新运行测试并确认通过**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=StaticConsoleResourceTests test
```

Expected:
- `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```powershell
cd F:\光明实验室\studyflow-ai
git add backend/src/main/resources/static/index.html backend/src/main/resources/static/app.js backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java
git commit -m "test: add static console resource coverage"
```

### Task 2: 实现页面骨架与统一样式

**Files:**
- Modify: `backend/src/main/resources/static/index.html`
- Create: `backend/src/main/resources/static/styles.css`
- Test: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`

- [ ] **Step 1: 补充结构化页面骨架**

`backend/src/main/resources/static/index.html`

```html
<body>
  <div class="page-shell">
    <header class="hero">
      <div>
        <p class="eyebrow">Backend Test Console</p>
        <h1>StudyFlow AI 静态测试台</h1>
        <p class="hero-copy">在一个页面里联调认证、上传、解析、问答和学习辅助能力。</p>
      </div>
      <div class="hero-status" id="tokenStatus">未登录</div>
    </header>

    <main class="dashboard">
      <section class="card" id="systemCard"></section>
      <section class="card" id="authCard"></section>
      <section class="card" id="uploadCard"></section>
      <section class="card" id="parseCard"></section>
      <section class="card" id="qaCard"></section>
      <section class="card" id="studyCard"></section>
      <section class="card" id="failureCard"></section>
      <section class="card card-full" id="logCard"></section>
    </main>
  </div>
  <script src="/app.js"></script>
</body>
```

- [ ] **Step 2: 添加统一样式**

`backend/src/main/resources/static/styles.css`

```css
:root {
  --bg: #f6f1e8;
  --panel: #fffdf8;
  --panel-border: #e4d7c3;
  --text: #2f2419;
  --muted: #7d6a57;
  --accent: #1d6b57;
  --accent-strong: #165544;
  --danger: #a63b32;
  --shadow: 0 18px 40px rgba(73, 49, 19, 0.08);
}

body {
  margin: 0;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  background: radial-gradient(circle at top, #fff8ef 0%, var(--bg) 55%, #efe4d4 100%);
  color: var(--text);
}

.dashboard {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
}

.card {
  background: var(--panel);
  border: 1px solid var(--panel-border);
  border-radius: 20px;
  box-shadow: var(--shadow);
  padding: 20px;
}

.card-full {
  grid-column: 1 / -1;
}
```

- [ ] **Step 3: 在首页中引入样式文件**

```html
<link rel="stylesheet" href="/styles.css">
```

- [ ] **Step 4: 回归静态资源测试**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=StaticConsoleResourceTests test
```

Expected:
- `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```powershell
cd F:\光明实验室\studyflow-ai
git add backend/src/main/resources/static/index.html backend/src/main/resources/static/styles.css
git commit -m "feat: add static console layout"
```

### Task 3: 实现系统状态、认证区和通用请求封装

**Files:**
- Modify: `backend/src/main/resources/static/index.html`
- Modify: `backend/src/main/resources/static/app.js`
- Modify: `backend/src/main/resources/static/styles.css`
- Test: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`

- [ ] **Step 1: 在页面中加入系统区和认证区表单**

在 `index.html` 中为以下元素提供固定容器和表单字段：
- 健康检查按钮与结果区
- 注册表单：`username`、`password`、`nickname`、`avatar`
- 登录表单：`username`、`password`
- 当前用户按钮与结果区
- token 展示与清除按钮

- [ ] **Step 2: 在 `app.js` 中实现通用请求封装**

```javascript
const storageKeys = {
  token: "studyflow.token",
  materialId: "studyflow.latestMaterialId",
  uploadId: "studyflow.latestUploadId",
  sessionId: "studyflow.latestSessionId"
};

async function apiRequest(path, options = {}) {
  const token = localStorage.getItem(storageKeys.token);
  const headers = new Headers(options.headers || {});
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  writeLog({ path, method: options.method || "GET", ok: response.ok, payload });
  return payload;
}
```

- [ ] **Step 3: 实现系统区与认证区交互**

至少补齐这些处理函数：
- `handleHealthCheck`
- `handleRegister`
- `handleLogin`
- `handleCurrentUser`
- `handleClearToken`
- `renderTokenStatus`

- [ ] **Step 4: 运行局部测试并人工验证静态页能打开**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=StaticConsoleResourceTests test
```

Manual:
- 启动后端
- 打开 `http://localhost:8080/index.html`
- 确认能看到认证区和系统区

- [ ] **Step 5: 提交**

```powershell
cd F:\光明实验室\studyflow-ai
git add backend/src/main/resources/static/index.html backend/src/main/resources/static/styles.css backend/src/main/resources/static/app.js
git commit -m "feat: add static console auth section"
```

### Task 4: 实现资料上传区与解析结果区

**Files:**
- Modify: `backend/src/main/resources/static/index.html`
- Modify: `backend/src/main/resources/static/app.js`
- Modify: `backend/src/main/resources/static/styles.css`
- Test: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`

- [ ] **Step 1: 在页面中加入资料上传与查询表单**

表单至少覆盖：
- 普通上传
- 分片上传初始化
- 单分片上传
- 已上传分片查询
- 分片完成
- 资料详情
- 我的资料列表
- 解析任务查询
- 文本内容查询
- 摘要查询与生成
- 转写查询

- [ ] **Step 2: 在 `app.js` 中实现上传与查询处理函数**

至少补齐这些函数：
- `handleMaterialUpload`
- `handleInitUpload`
- `handleUploadChunk`
- `handleCheckChunks`
- `handleCompleteUpload`
- `handleMaterialDetail`
- `handleMyMaterials`
- `handleParseTasks`
- `handleMaterialContent`
- `handleMaterialSummary`
- `handleGenerateSummary`
- `handleMediaTranscript`

- [ ] **Step 3: 为最近一次 materialId 和 uploadId 做自动回填**

```javascript
function saveLatestValue(key, value) {
  if (value !== undefined && value !== null && value !== "") {
    localStorage.setItem(key, String(value));
  }
}

function fillLatestValues() {
  document.querySelectorAll("[data-fill-material-id]").forEach((input) => {
    input.value = input.value || localStorage.getItem(storageKeys.materialId) || "";
  });
}
```

- [ ] **Step 4: 回归静态资源测试并做一次人工上传冒烟**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=StaticConsoleResourceTests test
```

Manual:
- 登录
- 上传一个 `txt` 或 `pdf`
- 查询资料列表
- 查询摘要或解析任务

- [ ] **Step 5: 提交**

```powershell
cd F:\光明实验室\studyflow-ai
git add backend/src/main/resources/static/index.html backend/src/main/resources/static/app.js
git commit -m "feat: add static console material operations"
```

### Task 5: 实现问答区、学习辅助区和失败任务区

**Files:**
- Modify: `backend/src/main/resources/static/index.html`
- Modify: `backend/src/main/resources/static/app.js`
- Modify: `backend/src/main/resources/static/styles.css`
- Test: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`

- [ ] **Step 1: 在页面中加入问答、学习辅助和失败任务表单**

表单至少覆盖：
- 创建问答会话
- 提问
- 问答历史
- 生成复习提纲
- 生成考试计划
- 学习计划历史
- 学习计划详情
- 失败任务列表
- 手动补偿

- [ ] **Step 2: 在 `app.js` 中实现对应处理函数**

至少补齐这些函数：
- `handleCreateQaSession`
- `handleAskQuestion`
- `handleQaHistory`
- `handleGenerateReviewOutline`
- `handleGenerateExamPlan`
- `handleStudyPlanHistory`
- `handleStudyPlanDetail`
- `handleFailureRecords`
- `handleCompensateFailure`

- [ ] **Step 3: 实现全局请求日志区和统一结果渲染**

```javascript
function writeLog(entry) {
  const log = document.getElementById("requestLog");
  const line = `[${new Date().toLocaleTimeString()}] ${entry.method} ${entry.path} -> ${entry.ok ? "OK" : "FAIL"}`;
  log.textContent = `${line}\n${log.textContent}`.trim();
}

function renderJson(targetId, payload) {
  const el = document.getElementById(targetId);
  el.textContent = JSON.stringify(payload, null, 2);
}
```

- [ ] **Step 4: 运行全量测试**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd test
```

Expected:
- `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```powershell
cd F:\光明实验室\studyflow-ai
git add backend/src/main/resources/static/index.html backend/src/main/resources/static/styles.css backend/src/main/resources/static/app.js
git commit -m "feat: add static console qa and study tools"
```

### Task 6: 补充文档并做端到端验证

**Files:**
- Modify: `backend/README.md`
- Test: `backend/src/test/java/com/studyflow/ai/StaticConsoleResourceTests.java`

- [ ] **Step 1: 在 README 增加静态测试页访问说明**

补充内容：
- 页面地址：`http://localhost:8080/index.html`
- 使用顺序建议：登录 -> 上传 -> 查询 -> 问答 -> 学习计划
- 需要先启动 Docker 依赖与后端服务

- [ ] **Step 2: 做端到端手工冒烟**

Checklist:
- 打开 `index.html`
- 注册或登录
- 调用健康检查
- 上传一个资料
- 查询资料列表
- 创建问答会话并提问
- 生成复习提纲或考试计划

- [ ] **Step 3: 运行最终回归**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd test
```

- [ ] **Step 4: 提交**

```powershell
cd F:\光明实验室\studyflow-ai
git add backend/README.md
git commit -m "docs: add static console usage guide"
```
