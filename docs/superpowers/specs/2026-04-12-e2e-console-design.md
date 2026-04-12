# E2E Console Design

## Goal

Add a simple Chinese end-to-end test page for StudyFlow AI that demonstrates the real LangChain4j + Milvus RAG flow without replacing the existing comprehensive static console.

## Design

Create a new static page at `/e2e.html` with its own `e2e.css` and `e2e.js`. The page is a guided workflow:

1. Health check.
2. Register or login and store the JWT token in `localStorage`.
3. Upload a material file through `/api/materials/upload`.
4. Query material detail and parse tasks.
5. Optionally dispatch parsing manually.
6. Query parsed content and AI summary.
7. Generate summary manually if needed.
8. Create a QA session.
9. Ask a RAG question against the uploaded material.
10. Show request logs and latest IDs.

The page should be intentionally simpler than the existing `index.html`, with Chinese labels, clear prerequisites, and automatic `materialId` / `sessionId` propagation.

## LangChain4j Enablement

Do not commit secrets. Keep real keys in `backend/config/application-local.yml`. The repository README should explain that true E2E RAG requires:

- `studyflow.ai.provider=langchain4j`
- `studyflow.embedding.provider=langchain4j`
- `studyflow.langchain4j.chat-model=qwen-plus`
- `studyflow.langchain4j.embedding-model=text-embedding-v4`
- Milvus started by Docker Compose

## Testing

Extend `StaticConsoleResourceTests` to verify `/e2e.html`, `/e2e.js`, and `/e2e.css` are served and contain stable markers.
