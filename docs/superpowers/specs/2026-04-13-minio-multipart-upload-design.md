# MinIO Multipart Upload Redesign

## Goal

将 StudyFlow AI 当前“业务分片对象上传 + Redis 记录 chunkIndex + 应用层本地合并”的大文件上传链路，重构为 MinIO 原生 multipart upload 方案，提升上传可靠性、减少应用层 I/O，并为断点续传和后续生产化扩展建立标准协议基础。

## Background

当前上传链路的问题：

1. 每个业务 chunk 先作为普通对象写入 MinIO 临时路径。
2. `completeUpload` 阶段由应用服务从 MinIO 下载全部 chunk，再写入本地临时文件，再上传回正式对象路径。
3. 该方案虽然可用，但会产生额外的网络传输、本地磁盘占用和应用层合并耗时。

用户已明确接受：

1. 上传模块可以进行不兼容重构。
2. 现有上传相关数据可清理，以保证新方案的一致性。
3. 目标方案为 MinIO 原生 multipart upload，而非保留当前 chunk 临时对象模型。

## Scope

本次改造聚焦：

1. 上传会话模型升级为 multipart 语义。
2. StorageGateway 扩展 MinIO multipart upload 能力。
3. Redis 上传状态从 `chunkIndex` 集合改为 `partNumber -> ETag` 映射。
4. 上传接口升级为 multipart 语义，但保留现有大致 URL 风格。
5. 去除应用层本地临时文件合并逻辑。
6. 增加 abort 和超时清理能力。

本次不包含：

1. 前端直传 MinIO 的预签名方案。
2. 跨用户文件去重。
3. 秒传。
4. 多节点上传协调。

## Architecture

### Recommended Approach

采用“后端托管的 MinIO 原生 multipart upload”：

1. 前端仍然只调用业务后端接口。
2. 后端在 `init` 阶段向 MinIO 发起 multipart upload，获取 `storageUploadId`。
3. 前端按后端返回的 `partSize` 使用 `File.slice()` 切片，并逐 part 上传到后端。
4. 后端收到每个 part 后，调用 MinIO upload part，并获取该 part 的 `ETag`。
5. Redis 保存 `partNumber -> ETag` 映射，用于断点续传和 complete。
6. `complete` 阶段由后端读取全部 part 信息，调用 MinIO complete multipart upload，直接生成正式对象。

### Why This Approach

相比当前方案，该方案：

1. 去掉应用层“下载分片 -> 本地合并 -> 再上传”的二次 I/O。
2. 去掉应用服务器本地临时合并文件。
3. 保留当前系统的业务鉴权、审计、状态控制能力。
4. 比前端直传 MinIO 方案风险更低、改造范围更可控。

## Data Model

### upload_session

保留 `upload_session` 表名，但升级字段语义。

建议结构：

- `id`
- `upload_id`
- `storage_upload_id`
- `material_id`
- `user_id`
- `file_name`
- `file_type`
- `file_size`
- `file_md5`
- `part_size`
- `total_parts`
- `uploaded_parts`
- `object_key`
- `material_type`
- `status`
- `source_type`
- `fail_reason`
- `expire_time`
- `create_time`
- `update_time`

### Status Model

新的 `UploadSessionStatusEnum`：

- `INIT`
- `UPLOADING`
- `COMPLETING`
- `COMPLETED`
- `ABORTED`
- `FAILED`
- `EXPIRED`

## Redis Design

### Session Metadata

Key:

`studyflow:upload:session:{uploadId}`

Type:

`Hash`

Fields:

- `storageUploadId`
- `materialId`
- `userId`
- `fileMd5`
- `fileSize`
- `partSize`
- `totalParts`
- `status`
- `expireTime`

### Uploaded Parts

Key:

`studyflow:upload:parts:{uploadId}`

Type:

`Hash`

Fields:

- `1 -> etag1`
- `2 -> etag2`
- `3 -> etag3`

使用 `Hash` 的原因是 complete multipart upload 需要完整的 `partNumber + ETag` 对，而不仅是“哪些 part 已上传”。

### TTL

默认 TTL 继续采用 24 小时。过期后：

1. Redis 状态自然失效。
2. DB 中会话标记为 `EXPIRED`。
3. MinIO 未完成 multipart upload 执行 abort。

## API Design

### 1. Init Upload

`POST /api/material-uploads/init`

Request:

- `fileName`
- `fileSize`
- `fileMd5`

Response:

- `uploadId`
- `materialId`
- `partSize`
- `totalParts`
- `status`

说明：

1. 后端负责计算推荐 `partSize`。
2. 前端按返回值进行切片，避免客户端各自使用不一致的 part 策略。

### 2. Upload Part

`POST /api/material-uploads/part`

Request:

- `uploadId`
- `partNumber`
- `part`

Response:

- `uploadId`
- `partNumber`
- `etag`
- `uploadedPartCount`
- `completed`

### 3. List Uploaded Parts

`GET /api/material-uploads/{uploadId}/parts`

Response:

- `uploadId`
- `totalParts`
- `uploadedPartCount`
- `uploadedParts` (`partNumber + etag`)
- `status`
- `completed`

### 4. Complete Upload

`POST /api/material-uploads/complete`

Request:

- `uploadId`

Response:

- `MaterialVO`

### 5. Abort Upload

`POST /api/material-uploads/abort`

Request:

- `uploadId`

Response:

- success result

## Multipart Flow

### Init

1. 校验用户和文件类型。
2. 创建 `material`，状态初始化为 `INIT`。
3. 生成业务 `uploadId`。
4. 生成正式 `objectKey`。
5. 调用 MinIO init multipart upload，获得 `storageUploadId`。
6. 计算 `partSize` 与 `totalParts`。
7. 创建 `upload_session`。
8. 初始化 Redis 状态。
9. 返回 `uploadId + partSize + totalParts`。

### Upload Part

1. 校验 `uploadId`、`userId`、`partNumber`。
2. 校验会话未处于 `COMPLETED / ABORTED / EXPIRED`。
3. 调用 MinIO upload part。
4. 获取该 part 的 `ETag`。
5. Redis 写入 `partNumber -> ETag`。
6. 更新 `upload_session.uploaded_parts` 和 `status=UPLOADING`。
7. 返回 part 上传结果。

### Resume

1. 前端调用 list uploaded parts。
2. 后端优先从 Redis 读取 `partNumber -> ETag`。
3. 若 Redis 缺失或不完整，则调用 MinIO list parts 兜底恢复。
4. 前端仅补传缺失 part。

### Complete

1. 校验会话状态。
2. 从 Redis 读取全部 `partNumber -> ETag`。
3. 校验数量等于 `totalParts`。
4. 校验 `partNumber` 连续且从 1 开始。
5. 更新状态为 `COMPLETING`。
6. 调用 MinIO complete multipart upload。
7. 更新 `material.upload_status=SUCCESS`、`material.parse_status=UPLOADED`。
8. 更新 `upload_session.status=COMPLETED`。
9. 清理 Redis 上传状态。
10. 投递初始解析任务。

### Abort

1. 校验会话存在且属于当前用户。
2. 若会话未完成，则调用 MinIO abort multipart upload。
3. 更新 `upload_session.status=ABORTED`。
4. 清理 Redis 状态。

## Part Size Strategy

后端统一下发推荐 part size，默认采用 `8MB`。

原因：

1. multipart upload 中除最后一个 part 外，前面的 part 一般至少应为 5MB。
2. 8MB 比 5MB 请求数量更少。
3. 8MB 又比 16MB、32MB 这类更大的 part 有更低的失败重传成本。
4. 对当前学习资料上传场景是较均衡的默认值。

后续可根据文件大小动态调整：

- 小于 100MB：8MB
- 100MB ~ 1GB：16MB
- 大于 1GB：32MB

本次先不做动态策略，先落地固定 8MB。

## Error Handling

### Duplicate Part Upload

同一个 `partNumber` 重复上传时：

1. 允许覆盖。
2. 以最后一次成功返回的 `ETag` 为准更新 Redis。

### Missing Parts on Complete

若 `uploadedParts < totalParts`，则直接拒绝 complete，返回统一业务错误。

### Redis Loss

Redis 不是 multipart part 信息的唯一真相源。若 Redis 丢失：

1. 通过 MinIO list parts 获取已上传 part 列表。
2. 回填 Redis。
3. 继续支持 resume 和 complete。

### Complete Success but DB Failure

MinIO complete 成功后若 DB 更新失败：

1. 记录失败日志和补偿日志。
2. 禁止再次调用 complete。
3. 通过补偿任务修复 `material` 和 `upload_session` 状态。

### Session Expiration

超时未完成的上传会话：

1. 标记 `EXPIRED`
2. 调用 MinIO abort
3. 清理 Redis

## Migration

由于用户允许清理旧数据，本次采用直接迁移：

1. 删除旧上传相关 Redis key。
2. 清理旧 MinIO `upload-sessions/` 临时对象。
3. 清理业务库中的 `upload_session` 与上传链路相关数据。
4. 使用新的 SQL 重建或升级上传会话结构。

## Testing

必须覆盖：

1. init 成功创建 multipart upload 会话。
2. upload part 成功返回 `ETag` 并写入 Redis。
3. 重复上传同一 `partNumber` 时 Redis 正确覆盖。
4. list uploaded parts 正常返回。
5. Redis 丢失时可通过 MinIO list parts 恢复。
6. 缺失 part 时 complete 被拒绝。
7. 全部 part 齐全时 complete 成功。
8. abort 成功后状态与 Redis 清理正确。
9. 上传会话超时后能正确 abort。

## Risks

1. MinIO multipart upload 的 SDK 交互与当前 StorageGateway 接口差异较大，需要补新的抽象。
2. Redis 丢失恢复逻辑必须做好，否则会影响断点续传体验。
3. complete 成功后的状态补偿需要谨慎设计，避免对象已存在但 DB 未更新。
4. 前端需要按后端返回的 `partSize` 切片，否则可能产生协议不一致。

## Recommendation

按以下顺序实施：

1. 先扩展 StorageGateway 的 multipart 能力。
2. 再升级 upload_session 模型和 Redis 结构。
3. 然后切换 service/controller/DTO/VO。
4. 最后补 abort、恢复与测试。

这是在当前代码基础上风险最低、收益最高的 multipart upload 重构路径。
