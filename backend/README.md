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
