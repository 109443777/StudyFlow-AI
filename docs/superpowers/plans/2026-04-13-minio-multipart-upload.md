# MinIO Multipart Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current chunk-object merge upload flow with native MinIO multipart upload while preserving business upload sessions, authentication, and asynchronous parse dispatch after upload completion.

**Architecture:** The backend remains the upload coordinator. `UploadSessionService` will create a MinIO multipart upload session, persist the returned storage upload ID, accept individual parts, store `partNumber -> ETag` state in Redis, and call MinIO complete/abort APIs. The current temporary chunk-object path and local merge file logic will be removed entirely.

**Tech Stack:** Spring Boot 3, Java 17, MyBatis-Plus, MinIO Java SDK, Redis, MySQL, JUnit 5, MockMvc

---

## File Map

### SQL
- Modify: `backend/src/main/resources/sql/003_upload_session.sql`
- Modify: `backend/docker/mysql/init/001-init.sql`

### Config / enums / utilities
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/studyflow/ai/config/UploadProperties.java`
- Modify: `backend/src/main/java/com/studyflow/ai/enums/UploadSessionStatusEnum.java`
- Modify: `backend/src/main/java/com/studyflow/ai/common/util/MaterialFileSupport.java`

### DTO / VO / entity
- Modify: `backend/src/main/java/com/studyflow/ai/entity/UploadSession.java`
- Modify: `backend/src/main/java/com/studyflow/ai/dto/InitUploadDTO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/dto/UploadChunkDTO.java`
- Add: `backend/src/main/java/com/studyflow/ai/dto/AbortUploadDTO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/vo/InitUploadVO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/vo/ChunkUploadVO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/vo/UploadedChunksVO.java`
- Add: `backend/src/main/java/com/studyflow/ai/vo/UploadedPartVO.java`

### Storage / Redis / service
- Modify: `backend/src/main/java/com/studyflow/ai/gateway/StorageGateway.java`
- Modify: `backend/src/main/java/com/studyflow/ai/gateway/MinioStorageGateway.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/upload/UploadProgressCache.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/upload/RedisUploadProgressCache.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/upload/InMemoryUploadProgressCache.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/UploadSessionService.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/impl/UploadSessionServiceImpl.java`
- Modify: `backend/src/main/java/com/studyflow/ai/controller/UploadSessionController.java`

### Tests
- Modify: `backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java`
- Add: `backend/src/test/java/com/studyflow/ai/MinioMultipartUploadGatewayTests.java`

---

### Task 1: Upgrade Upload Session Schema And API Contracts

**Files:**
- Modify: `backend/src/main/resources/sql/003_upload_session.sql`
- Modify: `backend/docker/mysql/init/001-init.sql`
- Modify: `backend/src/main/java/com/studyflow/ai/entity/UploadSession.java`
- Modify: `backend/src/main/java/com/studyflow/ai/enums/UploadSessionStatusEnum.java`
- Modify: `backend/src/main/java/com/studyflow/ai/config/UploadProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/studyflow/ai/dto/InitUploadDTO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/dto/UploadChunkDTO.java`
- Add: `backend/src/main/java/com/studyflow/ai/dto/AbortUploadDTO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/vo/InitUploadVO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/vo/ChunkUploadVO.java`
- Modify: `backend/src/main/java/com/studyflow/ai/vo/UploadedChunksVO.java`
- Add: `backend/src/main/java/com/studyflow/ai/vo/UploadedPartVO.java`

- [ ] **Step 1: Write the failing schema/API test expectations**

```java
@Test
void initUploadShouldReturnPartMetadata() throws Exception {
    mockMvc.perform(post("/api/material-uploads/init")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"fileName":"lesson.mp4","fileSize":33554432,"fileMd5":"0123456789abcdef0123456789abcdef"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadId").isNotEmpty())
            .andExpect(jsonPath("$.data.partSize").value(8388608))
            .andExpect(jsonPath("$.data.totalParts").value(4))
            .andExpect(jsonPath("$.data.status").value("INIT"));
}
```

- [ ] **Step 2: Run the upload integration test to verify it fails**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: FAIL because `partSize`, `totalParts`, and new multipart fields are not present yet.

- [ ] **Step 3: Update schema, enums, DTOs, VOs, and entity to multipart semantics**

```sql
ALTER TABLE upload_session
    ADD COLUMN storage_upload_id VARCHAR(128) NOT NULL,
    ADD COLUMN part_size BIGINT NOT NULL,
    ADD COLUMN total_parts INT NOT NULL,
    ADD COLUMN fail_reason VARCHAR(512) NULL,
    ADD COLUMN expire_time TIMESTAMP NULL;
```

```java
public enum UploadSessionStatusEnum {
    INIT,
    UPLOADING,
    COMPLETING,
    COMPLETED,
    ABORTED,
    FAILED,
    EXPIRED
}
```

```java
@Data
public class AbortUploadDTO {
    @NotBlank(message = "uploadId cannot be blank")
    private String uploadId;
}
```

```java
@Data
@Builder
public class InitUploadVO {
    private String uploadId;
    private Long materialId;
    private Long partSize;
    private Integer totalParts;
    private String status;
}
```

- [ ] **Step 4: Run the targeted test again**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: still FAIL, but now due to service and controller logic not yet matching multipart behavior.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/sql/003_upload_session.sql backend/docker/mysql/init/001-init.sql backend/src/main/java/com/studyflow/ai/entity/UploadSession.java backend/src/main/java/com/studyflow/ai/enums/UploadSessionStatusEnum.java backend/src/main/java/com/studyflow/ai/config/UploadProperties.java backend/src/main/resources/application.yml backend/src/main/java/com/studyflow/ai/dto/InitUploadDTO.java backend/src/main/java/com/studyflow/ai/dto/UploadChunkDTO.java backend/src/main/java/com/studyflow/ai/dto/AbortUploadDTO.java backend/src/main/java/com/studyflow/ai/vo/InitUploadVO.java backend/src/main/java/com/studyflow/ai/vo/ChunkUploadVO.java backend/src/main/java/com/studyflow/ai/vo/UploadedChunksVO.java backend/src/main/java/com/studyflow/ai/vo/UploadedPartVO.java
git commit -m "refactor: upgrade upload session schema for multipart upload"
```

---

### Task 2: Add Native Multipart Operations To Storage Gateway

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/gateway/StorageGateway.java`
- Modify: `backend/src/main/java/com/studyflow/ai/gateway/MinioStorageGateway.java`
- Add: `backend/src/test/java/com/studyflow/ai/MinioMultipartUploadGatewayTests.java`

- [ ] **Step 1: Write the failing gateway unit test**

```java
@Test
void shouldCompleteMultipartUploadFromUploadedParts() {
    String storageUploadId = storageGateway.initMultipartUpload("materials/u1/demo.mp4", "video/mp4");
    String etag1 = storageGateway.uploadPart("materials/u1/demo.mp4", storageUploadId, 1, inputStream1, 8L, "video/mp4");
    String etag2 = storageGateway.uploadPart("materials/u1/demo.mp4", storageUploadId, 2, inputStream2, 8L, "video/mp4");

    storageGateway.completeMultipartUpload(
            "materials/u1/demo.mp4",
            storageUploadId,
            List.of(new UploadedPart(1, etag1), new UploadedPart(2, etag2)));

    assertThat(storageGateway.getFileUrl("materials/u1/demo.mp4")).contains("materials/u1/demo.mp4");
}
```

- [ ] **Step 2: Run the new gateway test to verify it fails**

Run: `mvn -Dtest=MinioMultipartUploadGatewayTests test`

Expected: FAIL because `StorageGateway` does not yet expose multipart methods.

- [ ] **Step 3: Extend storage abstraction and implement MinIO multipart operations**

```java
public interface StorageGateway {
    String initMultipartUpload(String objectKey, String contentType);
    String uploadPart(String objectKey, String storageUploadId, int partNumber, InputStream inputStream, long size, String contentType);
    List<UploadedPartVO> listUploadedParts(String objectKey, String storageUploadId);
    void completeMultipartUpload(String objectKey, String storageUploadId, List<UploadedPartVO> uploadedParts);
    void abortMultipartUpload(String objectKey, String storageUploadId);
}
```

```java
public String uploadPart(...) {
    UploadPartResponse response = minioClient.uploadPart(
            UploadPartArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .uploadId(storageUploadId)
                    .partNumber(partNumber)
                    .stream(inputStream, size, -1)
                    .build());
    return response.etag();
}
```

- [ ] **Step 4: Run the gateway test again**

Run: `mvn -Dtest=MinioMultipartUploadGatewayTests test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/studyflow/ai/gateway/StorageGateway.java backend/src/main/java/com/studyflow/ai/gateway/MinioStorageGateway.java backend/src/test/java/com/studyflow/ai/MinioMultipartUploadGatewayTests.java
git commit -m "feat: add minio multipart upload gateway support"
```

---

### Task 3: Replace Redis Chunk Tracking With PartNumber-ETag Tracking

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/service/upload/UploadProgressCache.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/upload/RedisUploadProgressCache.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/upload/InMemoryUploadProgressCache.java`
- Modify: `backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java`

- [ ] **Step 1: Write the failing cache behavior test**

```java
@Test
void shouldStoreUploadedPartEtagMapping() {
    uploadProgressCache.initSession("u1", 8388608L, 3, "md5", "INIT", 10L, 20L, "storage-123");
    uploadProgressCache.saveUploadedPart("u1", 1, "\"etag-1\"");
    uploadProgressCache.saveUploadedPart("u1", 2, "\"etag-2\"");

    List<UploadedPartVO> parts = uploadProgressCache.getUploadedParts("u1");

    assertThat(parts).extracting(UploadedPartVO::getPartNumber).containsExactly(1, 2);
    assertThat(parts).extracting(UploadedPartVO::getEtag).containsExactly("\"etag-1\"", "\"etag-2\"");
}
```

- [ ] **Step 2: Run the upload integration test to verify it fails**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: FAIL because cache APIs still use chunk-based methods.

- [ ] **Step 3: Refactor cache interface and Redis implementation**

```java
public interface UploadProgressCache {
    void initSession(String uploadId, Long partSize, Integer totalParts, String fileMd5, String status, Long materialId, Long userId, String storageUploadId);
    void saveUploadedPart(String uploadId, Integer partNumber, String etag);
    List<UploadedPartVO> getUploadedParts(String uploadId);
    Optional<String> getUploadedPartEtag(String uploadId, Integer partNumber);
    void updateStatus(String uploadId, String status);
    void clear(String uploadId);
}
```

```java
stringRedisTemplate.opsForHash().put(buildPartsKey(uploadId), String.valueOf(partNumber), etag);
```

- [ ] **Step 4: Run the upload integration test again**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: still FAIL, but now due to service/controller behavior not yet updated.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/studyflow/ai/service/upload/UploadProgressCache.java backend/src/main/java/com/studyflow/ai/service/upload/RedisUploadProgressCache.java backend/src/main/java/com/studyflow/ai/service/upload/InMemoryUploadProgressCache.java backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java
git commit -m "refactor: track multipart etags in upload progress cache"
```

---

### Task 4: Rewrite UploadSessionService And Controller For Multipart Lifecycle

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/service/UploadSessionService.java`
- Modify: `backend/src/main/java/com/studyflow/ai/service/impl/UploadSessionServiceImpl.java`
- Modify: `backend/src/main/java/com/studyflow/ai/controller/UploadSessionController.java`
- Modify: `backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java`

- [ ] **Step 1: Write failing end-to-end tests for init, part upload, complete, and abort**

```java
@Test
void multipartUploadShouldCompleteWithoutLocalMerge() throws Exception {
    String uploadId = initUploadAndGetId("lesson.mp4", 16777216L);

    uploadPart(uploadId, 1, firstPartBytes);
    uploadPart(uploadId, 2, secondPartBytes);

    mockMvc.perform(post("/api/material-uploads/complete")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"uploadId":"%s"}
                            """.formatted(uploadId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.parseStatus").value("UPLOADED"));
}
```

```java
@Test
void abortShouldMarkSessionAbortedAndClearRedis() throws Exception {
    String uploadId = initUploadAndGetId("lesson.mp4", 16777216L);

    mockMvc.perform(post("/api/material-uploads/abort")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"uploadId":"%s"}
                            """.formatted(uploadId)))
            .andExpect(status().isOk());
}
```

- [ ] **Step 2: Run the integration suite to verify it fails**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: FAIL because controller/service still use chunk semantics and do not support abort.

- [ ] **Step 3: Implement multipart lifecycle in service and controller**

```java
public interface UploadSessionService {
    InitUploadVO initUpload(InitUploadDTO initUploadDTO);
    ChunkUploadVO uploadPart(UploadChunkDTO uploadChunkDTO);
    UploadedChunksVO listUploadedParts(String uploadId);
    MaterialVO completeUpload(CompleteUploadDTO completeUploadDTO);
    void abortUpload(AbortUploadDTO abortUploadDTO);
}
```

```java
String storageUploadId = storageGateway.initMultipartUpload(material.getObjectKey(), resolveContentType(extension));
uploadProgressCache.initSession(uploadId, partSize, totalParts, fileMd5, UploadSessionStatusEnum.INIT.name(), material.getId(), userId, storageUploadId);
```

```java
String etag = storageGateway.uploadPart(uploadSession.getObjectKey(), uploadSession.getStorageUploadId(), partNumber, inputStream, part.getSize(), part.getContentType());
uploadProgressCache.saveUploadedPart(uploadId, partNumber, etag);
```

```java
List<UploadedPartVO> uploadedParts = recoverUploadedParts(uploadSession);
storageGateway.completeMultipartUpload(uploadSession.getObjectKey(), uploadSession.getStorageUploadId(), uploadedParts);
```

- [ ] **Step 4: Run the multipart upload integration tests again**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/studyflow/ai/service/UploadSessionService.java backend/src/main/java/com/studyflow/ai/service/impl/UploadSessionServiceImpl.java backend/src/main/java/com/studyflow/ai/controller/UploadSessionController.java backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java
git commit -m "feat: switch upload session flow to minio multipart upload"
```

---

### Task 5: Add Recovery, Expiration Cleanup, And Final Verification

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/service/impl/UploadSessionServiceImpl.java`
- Modify: `backend/src/main/java/com/studyflow/ai/config/UploadProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java`

- [ ] **Step 1: Write failing tests for Redis-loss recovery and expiration cleanup**

```java
@Test
void completeShouldRecoverPartsFromMinioWhenRedisIsMissing() {
    // init upload, upload parts, clear redis, then complete
}

@Test
void expiredSessionShouldAbortMultipartUpload() {
    // create expired session and invoke cleanup path
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `mvn -Dtest=ChunkUploadIntegrationTests test`

Expected: FAIL because Redis recovery and expiration cleanup do not exist yet.

- [ ] **Step 3: Implement recovery and cleanup**

```java
private List<UploadedPartVO> recoverUploadedParts(UploadSession uploadSession) {
    List<UploadedPartVO> cached = uploadProgressCache.getUploadedParts(uploadSession.getUploadId());
    if (!cached.isEmpty()) {
        return cached;
    }
    List<UploadedPartVO> minioParts = storageGateway.listUploadedParts(uploadSession.getObjectKey(), uploadSession.getStorageUploadId());
    minioParts.forEach(part -> uploadProgressCache.saveUploadedPart(uploadSession.getUploadId(), part.getPartNumber(), part.getEtag()));
    return minioParts;
}
```

```java
public void abortExpiredUploads() {
    // scan db sessions with expireTime before now and status in INIT/UPLOADING/COMPLETING
    // abort multipart upload and mark EXPIRED
}
```

- [ ] **Step 4: Run full backend verification**

Run: `mvn test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/studyflow/ai/service/impl/UploadSessionServiceImpl.java backend/src/main/java/com/studyflow/ai/config/UploadProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/studyflow/ai/ChunkUploadIntegrationTests.java
git commit -m "feat: harden multipart upload recovery and cleanup"
```

---

## Self-Review

### Spec Coverage

- Native MinIO multipart upload: covered in Tasks 2 and 4.
- `upload_session` multipart semantics: covered in Task 1.
- Redis `partNumber -> ETag`: covered in Task 3.
- Abort and expiration cleanup: covered in Task 5.
- Redis loss fallback to MinIO list parts: covered in Task 5.
- Removal of local merge logic: covered in Task 4.

### Placeholder Scan

No `TODO`, `TBD`, or implicit “handle later” steps remain.

### Type Consistency

- API vocabulary is consistently `partNumber`, `partSize`, `totalParts`.
- Storage layer consistently uses `storageUploadId`.
- Progress cache consistently returns `UploadedPartVO`.

