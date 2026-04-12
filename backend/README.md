# StudyFlow AI 后端

StudyFlow AI 是一个面向大学生学习场景的多模态学习资料解析与智能问答平台。后端基于 Spring Boot 3、Java 17 和 Maven 构建。

当前后端已经具备以下能力：

- 用户注册、登录与 JWT 鉴权
- 普通资料上传与分片上传、断点续传
- RabbitMQ 异步解析任务中心
- PDF、Word、PPT、TXT、Markdown 文本解析
- 音频和视频转写链路
- AI 摘要、关键词、知识点和复习重点生成
- 文本切片、embedding 向量化与 RAG 问答
- 复习提纲与学习计划生成
- 幂等、限流、重试、失败补偿等系统治理能力

## 本地基础设施

在 `backend` 目录下启动依赖组件：

```bash
docker compose up -d
```

默认端口：

- MySQL：`3307`
- Redis：`6379`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`
- MinIO API：`9000`
- MinIO 控制台：`9001`
- Milvus：`19530`
- Milvus 健康检查/指标端口：`9091`

MySQL 默认使用 `3307` 的原因：

- 很多 Windows 电脑本地已经占用了 `3306`
- Compose 默认使用 `3307` 可以降低端口冲突概率

如果你本机没有占用 `3306`，也可以切回：

```bash
MYSQL_HOST_PORT=3306 docker compose up -d
```

查看容器状态：

```bash
docker compose ps
```

## 数据库初始化

Compose 会挂载 MySQL 初始化脚本目录：

```text
backend/docker/mysql/init
```

如果是全新的 MySQL volume，当前项目所需的数据表会自动创建。

## 本地应用配置

复制本地配置示例，并填入你自己的密钥：

```bash
cp application-local.example.yml config/application-local.yml
```

PowerShell：

```powershell
Copy-Item .\application-local.example.yml .\config\application-local.yml
```

注意：

- `config/application-local.yml` 已被 Git 忽略
- AI Key、转写 Key、本地环境覆盖配置都放在这里
- 如果使用默认 Docker MySQL 端口，应用 datasource 端口保持 `3307`

## RAG 与 Milvus 向量库

当前 RAG 向量检索默认走真实 Milvus：

```yaml
studyflow:
  vector-store:
    provider: milvus
    fallback-to-database: true
    milvus:
      uri: http://localhost:19530
      token:
      collection-name: studyflow_material_chunks
      dimension: 1024
      metric-type: COSINE
      batch-size: 64
```

完整链路：

```text
上传资料
-> 原始文件保存到 MinIO
-> 文档解析或音视频转写生成文本
-> 文本保存到 material_content
-> 按章节/段落优先切片，超长段落再滑动窗口切片
-> chunk 保存到 material_chunk
-> 调用 embedding gateway 生成向量
-> 向量写入 Milvus
-> MySQL 同步保留 embedding_vector 作为观测和降级检索数据
-> 用户提问时优先从 Milvus topK 召回
-> 拼接上下文，调用大模型生成基于资料的回答
-> 问答记录与引用 chunk 写入 qa_message
```

说明：

- Milvus 在 Docker Compose 中使用自己的内部 MinIO，这和 StudyFlow 用于保存上传资料的 MinIO 是两套用途，不要混淆。
- `dimension` 必须和 embedding 模型输出维度一致。当前阿里云 `text-embedding-v4` 按 `1024` 配置。
- `fallback-to-database: true` 表示 Milvus 暂时不可用时，RAG 检索可以降级到 MySQL 中的 embedding JSON 做余弦相似度召回。
- `batch-size` 控制每批写入 Milvus 的 chunk 数量，避免长 PDF 或视频转写文本一次性写入过大。
- 当前 Docker Compose 使用的是 Milvus standalone，适合本地开发和项目演示；真正生产级高可用应使用 Milvus Cluster 或托管向量数据库。

## AI 与 Embedding 配置

默认主配置里 AI 和 embedding 仍保留 `mock` 作为安全兜底，方便无 Key 时启动项目。你要测试真实 AI/RAG 效果时，需要在 `config/application-local.yml` 中启用 LangChain4j：

```yaml
studyflow:
  ai:
    provider: langchain4j
  embedding:
    provider: langchain4j
  langchain4j:
    chat-model: qwen-plus
    embedding-model: text-embedding-v4
    api-key: your-api-key
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    timeout: 30s
    max-retries: 2
```

如果 embedding 仍使用 `mock`，RAG 链路仍能跑通，但向量不是语义向量，不适合作为真实效果评估。

## 音视频转写配置

默认本地转写 provider 是 `mock`，因此没有外部 ASR 账号也能跑通音视频任务链路。

如果要测试阿里云百炼/DashScope 真实转写：

1. 安装 FFmpeg，并确保命令行能访问 `ffmpeg`
2. 配置 `config/application-local.yml`
3. 确保传给阿里云的音频 URL 能被阿里云访问。如果 MinIO 只运行在 `localhost`，云端 ASR 通常无法下载该文件，需要使用公网 MinIO、OSS URL 或其他可访问的对象地址

示例：

```yaml
studyflow:
  transcription:
    provider: external
    ffmpeg-path: ffmpeg
    temp-dir: ${java.io.tmpdir}/studyflow-media
    ffmpeg-timeout-seconds: 600
    external:
      base-url: https://dashscope.aliyuncs.com/api/v1
      api-key: your-dashscope-api-key
      model: paraformer-v2
      poll-interval-millis: 1000
      max-poll-attempts: 60
```

当前音视频处理链路：

- 音频：`MinIO URL -> 阿里云转写 -> media_transcript -> material_content`
- 视频：`MinIO 下载视频 -> FFmpeg 抽取 wav -> 临时音频对象 URL -> 阿里云转写 -> media_transcript -> material_content`

## 启动后端

默认启动：

```bash
./mvnw spring-boot:run
```

使用本地 profile：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

PowerShell：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

## 运行测试

```bash
./mvnw test
```

PowerShell：

```powershell
.\mvnw.cmd test
```

## 常用入口

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- 健康检查接口：`http://localhost:8080/api/health`
- 静态测试页面：`http://localhost:8080/index.html`
- RabbitMQ 管理台：`http://localhost:15672`
- MinIO 控制台：`http://localhost:9001`

## 静态测试页面

使用静态测试页面前，请先确认 `docker compose up -d` 已启动 `MySQL`、`Redis`、`RabbitMQ`、`MinIO` 和 `Milvus`，并且后端服务已经启动。

打开：

```text
http://localhost:8080/index.html
```

推荐测试顺序：

1. 健康检查
2. 登录
3. 上传资料
4. 查询解析任务
5. RAG 问答
6. 学习计划
7. 失败补偿

## 本地默认账号密码

仅用于本地开发：

- MySQL
  - 数据库：`studyflow_ai`
  - 用户名：`studyflow`
  - 密码：`studyflow123`
  - root 密码：`root123456`
- Redis
  - 密码：`redis123456`
- RabbitMQ
  - 用户名：`studyflow`
  - 密码：`studyflow123`
- MinIO
  - access key：`studyflow`
  - secret key：`studyflow123`
