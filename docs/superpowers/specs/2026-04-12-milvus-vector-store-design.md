# Milvus Vector Store Design

## Goal

Make StudyFlow AI's RAG retrieval use a real vector database when configured, while keeping the existing MySQL-backed vector store as a lightweight local fallback.

## Current State

- `VectorStoreService` already abstracts vector indexing and search.
- `DatabaseVectorStoreServiceImpl` stores embedding vectors as JSON in `material_chunk.embedding_vector` and ranks chunks in Java with cosine similarity.
- `EmbeddingGateway` already has a real LangChain4j implementation, so Milvus should reuse the existing embedding flow instead of introducing a new embedding provider.
- Local Docker currently starts MySQL, Redis, RabbitMQ, and MinIO only.

## Chosen Approach

Add `studyflow.vector-store.provider=database|milvus`.

- `database` remains the default to keep tests and simple local startup stable.
- `milvus` enables a new `MilvusVectorStoreServiceImpl`.
- The existing `VectorStoreService` interface stays unchanged, so `MaterialEmbeddingServiceImpl` and `RAGQueryServiceImpl` do not need behavioral changes.

## Milvus Collection Design

Collection name defaults to `studyflow_material_chunks`.

Fields:

- `chunk_id`: Int64 primary key, mapped from `material_chunk.id`.
- `material_id`: Int64 scalar field for filtering retrieval to one material.
- `chunk_index`: Int64 scalar field for display ordering/reference.
- `chunk_text`: VarChar field for returning context text.
- `embedding`: FloatVector field for semantic retrieval.

The vector dimension is configured with `studyflow.vector-store.milvus.dimension`. It must match the embedding model output dimension. For DashScope `text-embedding-v4`, local configuration should set the dimension according to the model output used by LangChain4j.

## Data Flow

Indexing:

1. `MaterialEmbeddingServiceImpl` chunks text and saves chunks in MySQL as before.
2. `MilvusVectorStoreServiceImpl` calls `EmbeddingGateway.embedDocuments(...)`.
3. It ensures the Milvus collection exists.
4. It upserts rows into Milvus by `chunk_id`.
5. It also writes the JSON embedding back to `material_chunk.embedding_vector` as a debug/fallback trace.

Query:

1. `RAGQueryServiceImpl` calls `VectorStoreService.searchByMaterialId(...)`.
2. `MilvusVectorStoreServiceImpl` embeds the question with `EmbeddingGateway.embedQuery(...)`.
3. It searches Milvus with filter `material_id == <id>` and topK.
4. It returns `ChunkSearchResult` using chunk metadata returned from Milvus.

## Error Handling

- If chunks are empty, keep throwing `MATERIAL_CONTENT_NOT_FOUND`.
- If Milvus is unavailable, throw `SYSTEM_BUSY` with a clear message.
- If no Milvus results are returned, throw `VECTOR_INDEX_NOT_READY`.
- If provider is `database`, existing behavior remains unchanged.

## Docker And Docs

Add Milvus standalone components to `backend/docker-compose.yml`: `milvus-etcd`, `milvus-minio`, and `milvus-standalone`. The app's existing MinIO service remains separate for uploaded materials to avoid coupling object storage credentials.

Document:

- how to enable Milvus with `STUDYFLOW_VECTOR_STORE_PROVIDER=milvus`;
- Milvus endpoint defaults;
- the distinction between StudyFlow file MinIO and Milvus internal MinIO.

## Non-Goals

- Do not remove the database vector store.
- Do not add hybrid search or BM25 in this iteration.
- Do not change the public RAG controller API.
- Do not require Milvus in ordinary unit/integration tests.
