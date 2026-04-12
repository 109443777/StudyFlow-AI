# StudyFlow AI Backend

StudyFlow AI backend project built with Spring Boot 3, Java 17, and Maven.

Current backend capabilities include:
- user registration and JWT login
- material upload and chunk upload resume
- async parse task center
- document text extraction
- audio and video transcription
- AI content understanding
- embedding and RAG question answering
- study outline and study plan generation
- governance features such as idempotency, rate limit, retry, and compensation

## Local Infra

Start the required infrastructure from the `backend` directory:

```bash
docker compose up -d
```

Default exposed ports:
- MySQL: `3307`
- Redis: `6379`
- RabbitMQ: `5672`
- RabbitMQ Management: `15672`
- MinIO API: `9000`
- MinIO Console: `9001`
- Milvus: `19530`
- Milvus Health/Metrics: `9091`

Why MySQL uses `3307`:
- Many Windows environments already have a local MySQL service on `3306`
- The compose file uses `3307` by default to avoid port conflicts

If your machine does not use `3306`, you can switch back:

```bash
MYSQL_HOST_PORT=3306 docker compose up -d
```

Check container status:

```bash
docker compose ps
```

## Database Bootstrap

The compose file mounts MySQL init scripts from:

```text
backend/docker/mysql/init
```

On a fresh MySQL volume, all current StudyFlow tables are created automatically.

## Local Application Config

Copy the example local config and fill in your own secrets:

```bash
cp application-local.example.yml config/application-local.yml
```

PowerShell:

```powershell
Copy-Item .\application-local.example.yml .\config\application-local.yml
```

Notes:
- `config/application-local.yml` is ignored by Git
- use it for AI keys and local overrides
- if you use the default Docker MySQL port, keep the datasource port as `3307`

## Vector Store Setup

RAG retrieval defaults to `database`, which stores embedding JSON in MySQL and is convenient for local smoke tests.

To use the real Milvus provider, start Docker Compose and update `config/application-local.yml`:

```yaml
studyflow:
  vector-store:
    provider: milvus
    milvus:
      uri: http://localhost:19530
      token:
      collection-name: studyflow_material_chunks
      dimension: 1024
      metric-type: COSINE
```

Notes:
- Milvus uses its own internal MinIO service in Docker Compose. This is separate from the StudyFlow file-storage MinIO used for uploaded documents, audio, and video.
- `dimension` must match the embedding model output dimension. If you change the embedding model, update `studyflow.vector-store.milvus.dimension` at the same time.
- The current Milvus collection stores `chunk_id`, `material_id`, `chunk_index`, `chunk_text`, and `embedding`, while MySQL keeps the original chunk metadata for fallback and debugging.

## Media Transcription Setup

The default local transcription provider is `mock`, so audio and video tasks can run without an external ASR account.

To test the external Aliyun Bailian/DashScope transcription path:

1. Install FFmpeg and make sure `ffmpeg` is available in `PATH`.
2. Configure `config/application-local.yml`.
3. Make sure the audio file URL passed to Aliyun is reachable by Aliyun. If MinIO runs only on `localhost`, the cloud ASR service usually cannot download it. Use a public MinIO endpoint, OSS URL, or another reachable object URL for real external transcription tests.

Example:

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

Current media flow:

- audio: `MinIO URL -> Aliyun transcription -> media_transcript -> material_content`
- video: `MinIO download -> FFmpeg extract wav -> temporary object URL -> Aliyun transcription -> media_transcript -> material_content`

## Run The App

Default startup:

```bash
./mvnw spring-boot:run
```

Run with local profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

## Run Tests

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

## Common Endpoints

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health API: `http://localhost:8080/api/health`
- Static test console: `http://localhost:8080/index.html`
- RabbitMQ Management: `http://localhost:15672`
- MinIO Console: `http://localhost:9001`

## Static Test Console

Before using the static test console, make sure `docker compose up -d` has started the required Docker services (`MySQL`, `Redis`, `RabbitMQ`, and `MinIO`), and that the backend service is running.

Open the console at:

```text
http://localhost:8080/index.html
```

Recommended usage order:

1. Health check (no login required)
2. Login
3. Upload
4. Parse query
5. Q&A
6. Study plan
7. Failure compensation

## Default Infra Credentials

For local development only:

- MySQL
  - database: `studyflow_ai`
  - username: `studyflow`
  - password: `studyflow123`
  - root password: `root123456`
- Redis
  - password: `redis123456`
- RabbitMQ
  - username: `studyflow`
  - password: `studyflow123`
- MinIO
  - access key: `studyflow`
  - secret key: `studyflow123`
