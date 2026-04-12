# Milvus Vector Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real Milvus-backed `VectorStoreService` provider for StudyFlow AI RAG retrieval.

**Architecture:** Keep `VectorStoreService` as the stable boundary. Add `VectorStoreProperties`, conditional provider configuration, and `MilvusVectorStoreServiceImpl`, while preserving `DatabaseVectorStoreServiceImpl` as the default local fallback. Docker Compose and README will document how to start Milvus and enable it.

**Tech Stack:** Java 17, Spring Boot 3, Maven, MyBatis-Plus, LangChain4j embeddings, Milvus Java SDK 2.5.14, Docker Compose, JUnit 5

---

## File Map

### Create

- `backend/src/main/java/com/studyflow/ai/config/VectorStoreProperties.java` - vector-store provider and Milvus connection/config properties.
- `backend/src/main/java/com/studyflow/ai/config/VectorStoreConfig.java` - conditional beans for database and Milvus `VectorStoreService`.
- `backend/src/main/java/com/studyflow/ai/service/impl/MilvusVectorStoreServiceImpl.java` - Milvus-backed indexing and retrieval implementation.
- `backend/src/test/java/com/studyflow/ai/VectorStoreProviderConfigTests.java` - verifies conditional provider wiring.
- `backend/src/test/java/com/studyflow/ai/MilvusVectorStoreServiceUnitTests.java` - verifies Milvus service behavior with mocked client facade if needed.

### Modify

- `backend/pom.xml` - add Milvus Java SDK dependency.
- `backend/src/main/java/com/studyflow/ai/service/impl/DatabaseVectorStoreServiceImpl.java` - make it conditional or move condition to config.
- `backend/src/main/resources/application.yml` - add `studyflow.vector-store` config.
- `backend/src/test/resources/application.yml` - keep tests on database provider by default.
- `backend/application-local.example.yml` - document optional Milvus provider config.
- `backend/docker-compose.yml` - add Milvus standalone stack.
- `backend/README.md` - document Milvus startup and provider selection.

---

## Task 1: Provider Configuration

**Files:**

- Create: `backend/src/main/java/com/studyflow/ai/config/VectorStoreProperties.java`
- Create: `backend/src/main/java/com/studyflow/ai/config/VectorStoreConfig.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/impl/DatabaseVectorStoreServiceImpl.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/studyflow/ai/VectorStoreProviderConfigTests.java`

- [ ] **Step 1: Write the failing provider wiring tests**

Create `backend/src/test/java/com/studyflow/ai/VectorStoreProviderConfigTests.java`:

```java
package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.impl.DatabaseVectorStoreServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "studyflow.vector-store.provider=database")
class VectorStoreProviderConfigTests {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void shouldUseDatabaseVectorStoreByDefault() {
        assertThat(vectorStoreService).isInstanceOf(DatabaseVectorStoreServiceImpl.class);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails before implementation**

Run:

```powershell
cd F:\光明实验室\studyflow-ai\.worktrees\milvus-vector-store\backend
.\mvnw.cmd -Dtest=VectorStoreProviderConfigTests test
```

Expected: FAIL because `studyflow.vector-store` properties/config do not exist yet.

- [ ] **Step 3: Implement properties and conditional database provider**

Create `VectorStoreProperties` with fields:

```java
package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.vector-store")
public class VectorStoreProperties {

    private String provider = "database";

    private Milvus milvus = new Milvus();

    @Data
    public static class Milvus {

        private String uri = "http://localhost:19530";

        private String token = "";

        private String collectionName = "studyflow_material_chunks";

        private Integer dimension = 1024;

        private String metricType = "COSINE";
    }
}
```

Create `VectorStoreConfig` and make database provider conditional with `@ConditionalOnProperty(name = "studyflow.vector-store.provider", havingValue = "database", matchIfMissing = true)`.

Update `application.yml`:

```yaml
studyflow:
  vector-store:
    provider: ${STUDYFLOW_VECTOR_STORE_PROVIDER:database}
    milvus:
      uri: ${MILVUS_URI:http://localhost:19530}
      token: ${MILVUS_TOKEN:}
      collection-name: ${MILVUS_COLLECTION_NAME:studyflow_material_chunks}
      dimension: ${MILVUS_DIMENSION:1024}
      metric-type: ${MILVUS_METRIC_TYPE:COSINE}
```

- [ ] **Step 4: Run the provider wiring test**

Run:

```powershell
.\mvnw.cmd -Dtest=VectorStoreProviderConfigTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/studyflow/ai/config/VectorStoreProperties.java backend/src/main/java/com/studyflow/ai/config/VectorStoreConfig.java backend/src/main/java/com/studyflow/ai/service/impl/DatabaseVectorStoreServiceImpl.java backend/src/main/resources/application.yml backend/src/test/java/com/studyflow/ai/VectorStoreProviderConfigTests.java
git commit -m "feat: add vector store provider configuration"
```

## Task 2: Milvus Service

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/studyflow/ai/service/impl/MilvusVectorStoreServiceImpl.java`
- Modify: `backend/src/main/java/com/studyflow/ai/config/VectorStoreConfig.java`
- Test: `backend/src/test/java/com/studyflow/ai/MilvusVectorStoreServiceUnitTests.java`

- [ ] **Step 1: Add Milvus SDK dependency**

Add to `backend/pom.xml`:

```xml
<milvus.version>2.5.14</milvus.version>
```

and:

```xml
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>${milvus.version}</version>
</dependency>
```

- [ ] **Step 2: Write unit tests for Milvus mapping helpers**

Create `MilvusVectorStoreServiceUnitTests` with tests for:

- converting `List<Double>` to `List<Float>`;
- rejecting dimension mismatches;
- building Milvus filter `material_id == 123`.

The tests should target package-private helper methods if the service exposes them package-locally, avoiding a live Milvus dependency.

- [ ] **Step 3: Run the unit test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=MilvusVectorStoreServiceUnitTests test
```

Expected: FAIL because `MilvusVectorStoreServiceImpl` does not exist.

- [ ] **Step 4: Implement `MilvusVectorStoreServiceImpl`**

Implementation requirements:

- Inject `VectorStoreProperties`, `EmbeddingGateway`, and `MaterialChunkMapper`.
- Create a `MilvusClientV2` from `ConnectConfig` in the constructor or a bean factory.
- Ensure collection exists before upsert/search.
- Use fields `chunk_id`, `material_id`, `chunk_index`, `chunk_text`, `embedding`.
- Use `UpsertReq` for indexing.
- Use `SearchReq` with `FloatVec`, `topK`, `filter("material_id == " + materialId)`, and output fields.
- Convert Milvus search hits back into `ChunkSearchResult`.
- Write embeddings back to `material_chunk.embedding_vector` JSON for fallback/debug consistency.

- [ ] **Step 5: Wire the Milvus provider**

Update `VectorStoreConfig`:

- `database` provider returns `DatabaseVectorStoreServiceImpl`.
- `milvus` provider returns `MilvusVectorStoreServiceImpl`.
- Missing provider falls back to database.

- [ ] **Step 6: Run Milvus-related tests**

Run:

```powershell
.\mvnw.cmd -Dtest=VectorStoreProviderConfigTests,MilvusVectorStoreServiceUnitTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/pom.xml backend/src/main/java/com/studyflow/ai/service/impl/MilvusVectorStoreServiceImpl.java backend/src/main/java/com/studyflow/ai/config/VectorStoreConfig.java backend/src/test/java/com/studyflow/ai/MilvusVectorStoreServiceUnitTests.java
git commit -m "feat: add milvus vector store service"
```

## Task 3: Docker And Documentation

**Files:**

- Modify: `backend/docker-compose.yml`
- Modify: `backend/application-local.example.yml`
- Modify: `backend/README.md`
- Modify: `backend/src/test/resources/application.yml`

- [ ] **Step 1: Add Milvus to local compose**

Add services:

- `milvus-etcd`
- `milvus-minio`
- `milvus-standalone`

Expose:

- Milvus gRPC/API: `19530`
- Milvus health/metrics: `9091`

Add volumes:

- `milvus_etcd_data`
- `milvus_minio_data`
- `milvus_data`

- [ ] **Step 2: Update local config example**

Add:

```yaml
  vector-store:
    provider: database
    milvus:
      uri: http://localhost:19530
      token:
      collection-name: studyflow_material_chunks
      dimension: 1024
      metric-type: COSINE
```

- [ ] **Step 3: Update README**

Document:

- default provider is `database`;
- enable real Milvus with `studyflow.vector-store.provider=milvus`;
- Milvus runs separate internal MinIO and does not replace StudyFlow file MinIO;
- dimension must match the embedding model.

- [ ] **Step 4: Run full tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add backend/docker-compose.yml backend/application-local.example.yml backend/README.md backend/src/test/resources/application.yml
git commit -m "docs: add milvus local setup"
```

## Self-Review

- Spec coverage: provider switch, Milvus service, Docker, README, and fallback behavior are covered.
- Placeholder scan: no TBD/TODO placeholders are intentionally left.
- Type consistency: provider property names use `studyflow.vector-store.*`; Milvus field names use `chunk_id`, `material_id`, `chunk_index`, `chunk_text`, and `embedding` consistently.
