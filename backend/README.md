# StudyFlow AI Backend

StudyFlow AI 后端项目，基于 Spring Boot 3、Java 17 与 Maven 构建，当前已实现用户鉴权、资料上传、分片上传、解析任务中心、文本解析、音视频转写、AI 内容理解、向量化检索、RAG 问答、学习计划与系统治理等核心模块。

## 本地启动

1. 启动基础依赖服务

```bash
cd backend
docker compose up -d
```

2. 检查容器状态

```bash
docker compose ps
```

3. 准备本地 AI 配置

项目支持通过工作目录下的 `config/` 目录覆盖默认配置。推荐复制示例文件后，按本地模型供应商填写：

```bash
cp application-local.example.yml config/application-local.yml
```

Windows PowerShell:

```powershell
Copy-Item .\application-local.example.yml .\config\application-local.yml
```

说明：
- `config/application-local.yml` 已被 Git 忽略，适合放本地密钥
- 如果暂时不接真实模型，保留默认 `mock` 配置即可

4. 启动项目

默认启动：

```bash
./mvnw spring-boot:run
```

启用本地 AI profile：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

5. 运行测试

```bash
./mvnw test
```

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

## 常用环境变量

- `MYSQL_HOST`，默认 `localhost`
- `MYSQL_PORT`，默认 `3306`
- `MYSQL_DB`，默认 `studyflow_ai`
- `MYSQL_USERNAME`，默认 `studyflow`
- `MYSQL_PASSWORD`，默认 `studyflow123`
- `REDIS_HOST`，默认 `localhost`
- `REDIS_PORT`，默认 `6379`
- `REDIS_PASSWORD`，默认 `redis123456`
- `RABBITMQ_HOST`，默认 `localhost`
- `RABBITMQ_PORT`，默认 `5672`
- `RABBITMQ_USERNAME`，默认 `studyflow`
- `RABBITMQ_PASSWORD`，默认 `studyflow123`
- `MINIO_ENDPOINT`，默认 `http://localhost:9000`
- `MINIO_ACCESS_KEY`，默认 `studyflow`
- `MINIO_SECRET_KEY`，默认 `studyflow123`
- `MINIO_BUCKET`，默认 `studyflow`

## AI 与 Embedding 本地示例

可参考 [application-local.example.yml](F:\光明实验室\studyflow-ai\backend\application-local.example.yml)：

- `studyflow.ai.provider=langchain4j`
- `studyflow.embedding.provider=langchain4j`
- `studyflow.langchain4j.chat-model`
- `studyflow.langchain4j.embedding-model`
- `studyflow.langchain4j.api-key`
- `studyflow.langchain4j.base-url`

如果使用 OpenAI 兼容接口，例如阿里云百炼，可直接填写兼容模式 `base-url`。

## 访问入口

- 服务地址：`http://localhost:8080`
- 健康检查：`http://localhost:8080/api/health`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- RabbitMQ 管理台：`http://localhost:15672`
- MinIO Console：`http://localhost:9001`

## 当前能力

- 统一返回体 `Result<T>`
- 全局异常处理与统一响应码
- MyBatis-Plus 基础配置
- 用户注册、登录、JWT 鉴权
- 普通上传、分片上传、断点续传
- 资料解析任务中心、重试、补偿、死信
- 文本解析与音视频转写
- AI 摘要、关键词、知识点、复习重点抽取
- 文本切块、向量化、RAG 问答
- 学习提纲与考试复习计划生成
- Redis 幂等、限流、会话缓存
