# RAG Milvus Hardening Design

## Goal

Enable the real Milvus vector store path for local RAG testing, fix Chinese text chunk boundaries, and make the RAG indexing/retrieval path more reliable without replacing the current Spring Boot module structure.

## Current Issues

- `TextChunkSupport` contains corrupted Chinese punctuation characters, so Chinese study notes do not split naturally on `。！？；，`.
- Chunking is mostly fixed-length sliding-window based. It does not prefer chapter and paragraph boundaries before falling back to sliding windows.
- Milvus can be enabled, but the service checks and loads the collection on every upsert/search.
- Milvus upsert embeds and writes all chunks in one batch, which can be too heavy for long PDFs or transcripts.
- If Milvus search fails, the current RAG path returns an error even though MySQL still stores embedding JSON that can be used as a degraded retrieval path.

## Design

### Chunking

Keep the public `TextChunkSupport.split(String text, int chunkSize, int overlap)` API. Internally, normalize the text, split it into chapter/paragraph blocks first, accumulate short blocks up to `chunkSize`, and only apply sliding-window splitting to blocks that exceed `chunkSize`.

Chapter detection supports Markdown headings (`# Chapter`), Chinese chapter headings (`第一章`, `第2节`, `第3讲`), and numeric headings (`1.`, `1.2`). Paragraph detection uses blank lines. Sentence boundary fallback supports English and Chinese punctuation.

### Milvus Provider

Make `milvus` the local example provider while keeping test resources on `database` so unit/integration tests do not require a live Milvus server.

Add properties:

- `studyflow.vector-store.fallback-to-database`
- `studyflow.vector-store.milvus.batch-size`

`MilvusVectorStoreServiceImpl` will:

- cache collection initialization with a volatile flag;
- validate embedding count and vector dimensions before writing;
- upsert chunks to Milvus in batches;
- still persist embedding JSON to MySQL for observability and fallback;
- on search failure, optionally fallback to `DatabaseVectorStoreServiceImpl`.

### RAG Flow

The normal path remains:

```text
material_content.cleaned_text
-> material_chunk
-> embedding
-> Milvus
-> topK chunk recall
-> prompt + context
-> AI answer
-> qa_message references
```

When Milvus is temporarily unavailable and fallback is enabled:

```text
Milvus search error
-> MySQL embedding_vector cosine search
-> prompt + context
-> AI answer
```

This is not a replacement for production Milvus cluster HA. It is application-level resilience suitable for local development, demos, and interview discussion. Production HA should use Milvus cluster or a managed vector database.

## Testing

- Add unit tests for Chinese punctuation, chapter/paragraph chunking, and long-block sliding-window behavior.
- Add unit tests for vector batch calculation and fallback property defaults.
- Keep full Maven tests green without requiring a live Milvus service.
