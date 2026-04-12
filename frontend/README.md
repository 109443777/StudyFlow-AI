# StudyFlow AI 前端

这是 StudyFlow AI 的第一版前后端分离测试工作台，基于 Vue 3 + Vite + TypeScript + Pinia + Vue Router + Axios + Element Plus 构建。

## 功能范围

- 登录与注册
- 首页健康检查与模型配置展示
- 资料普通上传、资料列表、资料详情
- 解析任务状态查看
- 解析文本与 AI 摘要查看
- 基于资料的 RAG 问答与引用 chunk 展示
- 复习提纲与 7 天学习计划生成

## 本地启动

先启动后端和依赖组件：

```powershell
cd F:\光明实验室\studyflow-ai\backend
docker compose up -d
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

再启动前端：

```powershell
cd F:\光明实验室\studyflow-ai\frontend
npm.cmd install
npm.cmd run dev
```

默认访问：

```text
http://localhost:5173
```

开发环境默认通过 Vite proxy 转发 `/api` 到 `http://localhost:8080`。如果需要显式配置后端地址，可以修改 `.env.development`：

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 验证命令

```powershell
npm.cmd run test
npm.cmd run typecheck
npm.cmd run build
```

## 健壮性设计

- 前端所有 ID 都按字符串处理，避免 JavaScript 大整数精度丢失。
- 所有 API 统一经过 `src/api/request.ts`，集中处理 JWT、超时、`Result<T>` 解包和中文错误提示。
- 对常见后端错误码做中文化提示，例如向量索引未就绪、AI 服务超时、资料解析文本未生成。
- 资料上传展示进度，问答和计划生成带 loading 状态，避免重复点击。
- 页面包含加载、空数据、失败重试等基础状态，便于端到端演示和排错。
