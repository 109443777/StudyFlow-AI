# RAG Milvus Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade StudyFlow AI chunking and harden the Milvus-backed RAG retrieval path.

**Architecture:** Keep the current `ChunkService`, `VectorStoreService`, and gateway boundaries. Improve `TextChunkSupport` behind its existing static API, then enhance `MilvusVectorStoreServiceImpl` with batching, collection initialization caching, and database fallback while keeping the database vector store as a separate implementation.

**Tech Stack:** Java 17, Spring Boot 3, Maven, MyBatis-Plus, MySQL/H2 tests, Milvus Java SDK, JUnit 5, AssertJ.

---

## File Map

- Modify: `backend/src/main/java/com/studyflow/ai/common/util/TextChunkSupport.java`
- Modify: `backend/src/main/java/com/studyflow/ai/config/VectorStoreProperties.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/impl/MilvusVectorStoreServiceImpl.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/application-local.example.yml`
- Modify: `backend/README.md`
- Test: `backend/src/test/java/com/studyflow/ai/TextChunkSupportTests.java`
- Test: `backend/src/test/java/com/studyflow/ai/MilvusVectorStoreServiceUnitTests.java`
- Test: `backend/src/test/java/com/studyflow/ai/VectorStoreProviderConfigTests.java`

## Task 1: Chunking Upgrade

- [ ] **Step 1: Write failing chunking tests**

Add `TextChunkSupportTests` cases:

- Chinese punctuation should split naturally and must not contain corrupted punctuation constants.
- Chapter/paragraph text should produce chunks that start at chapter boundaries when possible.
- One long paragraph should be split with overlap and a maximum chunk length.

Run:

```powershell
cd F:\光明实验室\studyflow-ai\.worktrees\rag-milvus-hardening\backend
.\mvnw.cmd -Dtest=TextChunkSupportTests test
```

Expected: fail before implementation.

- [ ] **Step 2: Implement chapter/paragraph-first chunking**

Update `TextChunkSupport`:

- use correct punctuation: `\n`, `.`, `?`, `!`, `;`, `,`, `。`, `？`, `！`, `；`, `，`;
- split into blocks by headings and blank-line paragraphs;
- accumulate short blocks up to `chunkSize`;
- split oversize blocks with sliding windows and overlap;
- keep `estimateTokenCount` unchanged.

- [ ] **Step 3: Verify and commit**

Run:

```powershell
.\mvnw.cmd -Dtest=TextChunkSupportTests test
```

Commit:

```powershell
git add backend/src/main/java/com/studyflow/ai/common/util/TextChunkSupport.java backend/src/test/java/com/studyflow/ai/TextChunkSupportTests.java
git commit -m "feat: improve rag text chunking"
```

## Task 2: Milvus Reliability And Fallback

- [ ] **Step 1: Write failing tests**

Extend Milvus/vector store tests for:

- default fallback flag is enabled;
- default Milvus batch size is positive;
- batch count calculation handles exact and partial batches.

Run:

```powershell
.\mvnw.cmd -Dtest=MilvusVectorStoreServiceUnitTests,VectorStoreProviderConfigTests test
```

Expected: fail before implementation.

- [ ] **Step 2: Implement properties**

Update `VectorStoreProperties`:

- add `fallbackToDatabase = true`;
- add `milvus.batchSize = 64`.

Update `application.yml` and local example config with env overrides.

- [ ] **Step 3: Implement Milvus hardening**

Update `MilvusVectorStoreServiceImpl`:

- inject `DatabaseVectorStoreServiceImpl` lazily through `ObjectProvider`;
- cache collection initialization in a volatile flag;
- validate embedding result count equals chunk count;
- upsert rows in batches based on `milvus.batchSize`;
- fallback to database search on Milvus search runtime failures when `fallbackToDatabase` is true;
- expose a package-visible `batchCount(int itemCount, int batchSize)` helper for tests.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -Dtest=MilvusVectorStoreServiceUnitTests,VectorStoreProviderConfigTests test
```

Commit:

```powershell
git add backend/src/main/java/com/studyflow/ai/config/VectorStoreProperties.java backend/src/main/java/com/studyflow/ai/service/impl/MilvusVectorStoreServiceImpl.java backend/src/main/resources/application.yml backend/application-local.example.yml backend/src/test/java/com/studyflow/ai/MilvusVectorStoreServiceUnitTests.java backend/src/test/java/com/studyflow/ai/VectorStoreProviderConfigTests.java
git commit -m "feat: harden milvus vector store"
```

## Task 3: Documentation And Full Verification

- [ ] **Step 1: Update README**

Document:

- local example now enables Milvus;
- MySQL fallback keeps RAG usable if Milvus is temporarily unavailable;
- standalone Milvus is not full production HA.

- [ ] **Step 2: Verify**

Run:

```powershell
.\mvnw.cmd test
docker compose -f docker-compose.yml config --quiet
```

- [ ] **Step 3: Commit**

```powershell
git add backend/README.md
git commit -m "docs: describe rag milvus hardening"
```

## Self-Review

- Spec coverage: chunking, Milvus enablement, fallback, batching, and full verification are covered.
- Placeholder scan: no TBD/TODO placeholders are used.
- Scope check: this plan intentionally stays in application-level reliability and does not implement Milvus cluster HA.
