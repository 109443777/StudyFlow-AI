# StudyFlow AI Backend

StudyFlow AI 后端骨架项目，基于 Spring Boot 3 + Java 17 + Maven，预留了 MyBatis-Plus、MySQL、Redis、RabbitMQ、MinIO、LangChain4j 等基础设施接入能力。

## 本地启动步骤

1. 启动基础依赖服务

```bash
cd backend
docker compose up -d
```

2. 检查容器状态

```bash
docker compose ps
```

3. 配置本地环境变量（可选）

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

4. 启动项目

```bash
./mvnw spring-boot:run
```

Windows PowerShell 下可使用：

```powershell
.\mvnw.cmd spring-boot:run
```

5. 运行测试

```bash
./mvnw test
```

## 访问入口

- 服务地址: `http://localhost:8080`
- 健康检查: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- RabbitMQ 管理台: `http://localhost:15672`
- MinIO Console: `http://localhost:9001`

## 当前骨架包含

- 统一响应体 `Result<T>`
- 统一响应码枚举
- 全局异常处理
- MyBatis-Plus 基础配置
- BaseEntity 审计字段
- MinIO / LangChain4j 配置预留
- RabbitMQ 常量定义
- 健康检查示例接口
