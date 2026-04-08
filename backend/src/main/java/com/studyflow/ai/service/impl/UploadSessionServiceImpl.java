package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.util.MaterialFileSupport;
import com.studyflow.ai.dto.CompleteUploadDTO;
import com.studyflow.ai.dto.InitUploadDTO;
import com.studyflow.ai.dto.UploadChunkDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.UploadSession;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.UploadSessionStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.service.MaterialService;
import com.studyflow.ai.service.ParseTaskService;
import com.studyflow.ai.service.UploadSessionService;
import com.studyflow.ai.service.upload.UploadProgressCache;
import com.studyflow.ai.vo.ChunkUploadVO;
import com.studyflow.ai.vo.InitUploadVO;
import com.studyflow.ai.vo.MaterialVO;
import com.studyflow.ai.vo.UploadedChunksVO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadSessionServiceImpl implements UploadSessionService {

    private final UploadSessionMapper uploadSessionMapper;

    private final MaterialMapper materialMapper;

    private final MaterialService materialService;

    private final ParseTaskService parseTaskService;

    private final StorageGateway storageGateway;

    private final UploadProgressCache uploadProgressCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InitUploadVO initUpload(InitUploadDTO initUploadDTO) {
        Long userId = requireUserId();
        String fileName = initUploadDTO.getFileName();
        String extension = MaterialFileSupport.extractExtension(fileName);
        MaterialTypeEnum materialTypeEnum = MaterialFileSupport.resolveMaterialType(fileName);

        Material material = new Material();
        material.setUserId(userId);
        material.setFileName(fileName);
        material.setFileType(extension);
        material.setFileSize(initUploadDTO.getFileSize());
        material.setObjectKey(MaterialFileSupport.buildMaterialObjectKey(userId, extension));
        material.setMaterialType(materialTypeEnum.name());
        material.setParseStatus(MaterialParseStatusEnum.INIT.name());
        material.setUploadStatus(MaterialUploadStatusEnum.INIT.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        UploadSession uploadSession = new UploadSession();
        uploadSession.setUploadId(uploadId);
        uploadSession.setMaterialId(material.getId());
        uploadSession.setUserId(userId);
        uploadSession.setFileName(fileName);
        uploadSession.setFileType(extension);
        uploadSession.setFileSize(initUploadDTO.getFileSize());
        uploadSession.setFileMd5(initUploadDTO.getFileMd5().toLowerCase(Locale.ROOT));
        uploadSession.setTotalChunks(initUploadDTO.getTotalChunks());
        uploadSession.setUploadedChunks(0);
        uploadSession.setObjectKey(material.getObjectKey());
        uploadSession.setMaterialType(materialTypeEnum.name());
        uploadSession.setStatus(UploadSessionStatusEnum.INIT.name());
        uploadSession.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        uploadSessionMapper.insert(uploadSession);

        uploadProgressCache.initSession(
                uploadId,
                initUploadDTO.getTotalChunks(),
                uploadSession.getFileMd5(),
                UploadSessionStatusEnum.INIT.name(),
                material.getId(),
                userId);

        return InitUploadVO.builder()
                .uploadId(uploadId)
                .materialId(material.getId())
                .totalChunks(initUploadDTO.getTotalChunks())
                .uploadedChunks(List.of())
                .status(uploadSession.getStatus())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChunkUploadVO uploadChunk(UploadChunkDTO uploadChunkDTO) {
        Long userId = requireUserId();
        UploadSession uploadSession = getSession(uploadChunkDTO.getUploadId(), userId);
        Integer chunkIndex = uploadChunkDTO.getChunkIndex();
        if (chunkIndex < 0 || chunkIndex >= uploadSession.getTotalChunks()) {
            throw new BusinessException(ResultCodeEnum.CHUNK_INDEX_INVALID);
        }
        if (UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus())) {
            return ChunkUploadVO.builder()
                    .uploadId(uploadSession.getUploadId())
                    .chunkIndex(chunkIndex)
                    .uploadedChunkCount(uploadSession.getTotalChunks())
                    .alreadyUploaded(true)
                    .completed(true)
                    .build();
        }
        if (uploadProgressCache.isChunkUploaded(uploadSession.getUploadId(), chunkIndex)) {
            return ChunkUploadVO.builder()
                    .uploadId(uploadSession.getUploadId())
                    .chunkIndex(chunkIndex)
                    .uploadedChunkCount(uploadSession.getUploadedChunks())
                    .alreadyUploaded(true)
                    .completed(false)
                    .build();
        }
        MultipartFile chunk = uploadChunkDTO.getChunk();
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.FILE_EMPTY);
        }
        try (InputStream inputStream = chunk.getInputStream()) {
            storageGateway.upload(
                    MaterialFileSupport.buildChunkObjectKey(uploadSession.getUploadId(), chunkIndex),
                    inputStream,
                    chunk.getSize(),
                    chunk.getContentType());
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to read chunk file");
        }
        Integer uploadedChunkCount = uploadProgressCache.markChunkUploaded(uploadSession.getUploadId(), chunkIndex);
        uploadProgressCache.updateStatus(uploadSession.getUploadId(), UploadSessionStatusEnum.UPLOADING.name());

        UploadSession updateSession = new UploadSession();
        updateSession.setId(uploadSession.getId());
        updateSession.setUploadedChunks(uploadedChunkCount);
        updateSession.setStatus(UploadSessionStatusEnum.UPLOADING.name());
        uploadSessionMapper.updateById(updateSession);

        return ChunkUploadVO.builder()
                .uploadId(uploadSession.getUploadId())
                .chunkIndex(chunkIndex)
                .uploadedChunkCount(uploadedChunkCount)
                .alreadyUploaded(false)
                .completed(uploadedChunkCount.equals(uploadSession.getTotalChunks()))
                .build();
    }

    @Override
    public UploadedChunksVO checkUploadedChunks(String uploadId) {
        Long userId = requireUserId();
        UploadSession uploadSession = getSession(uploadId, userId);
        List<Integer> uploadedChunks = UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus())
                ? buildAllChunkIndexes(uploadSession.getTotalChunks())
                : uploadProgressCache.getUploadedChunks(uploadId);
        return UploadedChunksVO.builder()
                .uploadId(uploadId)
                .totalChunks(uploadSession.getTotalChunks())
                .uploadedChunkCount(uploadedChunks.size())
                .uploadedChunks(uploadedChunks)
                .status(uploadSession.getStatus())
                .completed(UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialVO completeUpload(CompleteUploadDTO completeUploadDTO) {
        Long userId = requireUserId();
        UploadSession uploadSession = getSession(completeUploadDTO.getUploadId(), userId);
        if (UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus())) {
            return materialService.getMaterialDetail(uploadSession.getMaterialId());
        }
        List<Integer> uploadedChunks = uploadProgressCache.getUploadedChunks(uploadSession.getUploadId());
        if (uploadedChunks.size() != uploadSession.getTotalChunks()) {
            throw new BusinessException(ResultCodeEnum.UPLOAD_NOT_COMPLETE);
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("studyflow-merge-", "." + uploadSession.getFileType());
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                for (int index = 0; index < uploadSession.getTotalChunks(); index++) {
                    if (!uploadedChunks.contains(index)) {
                        throw new BusinessException(ResultCodeEnum.UPLOAD_NOT_COMPLETE);
                    }
                    try (InputStream inputStream = storageGateway.download(MaterialFileSupport.buildChunkObjectKey(uploadSession.getUploadId(), index))) {
                        inputStream.transferTo(outputStream);
                    }
                }
            }

            try (InputStream mergedStream = Files.newInputStream(tempFile)) {
                storageGateway.upload(uploadSession.getObjectKey(), mergedStream, uploadSession.getFileSize(), resolveContentType(uploadSession.getFileType()));
            }

            for (int index = 0; index < uploadSession.getTotalChunks(); index++) {
                storageGateway.delete(MaterialFileSupport.buildChunkObjectKey(uploadSession.getUploadId(), index));
            }

            Material material = new Material();
            material.setId(uploadSession.getMaterialId());
            material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
            material.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
            materialMapper.updateById(material);
            material = materialMapper.selectById(uploadSession.getMaterialId());
            log.info("Chunk upload completed and merged successfully, uploadId={}, materialId={}, totalChunks={}",
                    uploadSession.getUploadId(), uploadSession.getMaterialId(), uploadSession.getTotalChunks());
            parseTaskService.createAndDispatchInitialTask(material);

            UploadSession updateSession = new UploadSession();
            updateSession.setId(uploadSession.getId());
            updateSession.setUploadedChunks(uploadSession.getTotalChunks());
            updateSession.setStatus(UploadSessionStatusEnum.COMPLETED.name());
            uploadSessionMapper.updateById(updateSession);
            uploadProgressCache.updateStatus(uploadSession.getUploadId(), UploadSessionStatusEnum.COMPLETED.name());

            return materialService.getMaterialDetail(uploadSession.getMaterialId());
        } catch (IOException exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId());
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to merge upload chunks");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId());
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to complete upload");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private UploadSession getSession(String uploadId, Long userId) {
        UploadSession uploadSession = uploadSessionMapper.selectOne(new LambdaQueryWrapper<UploadSession>()
                .eq(UploadSession::getUploadId, uploadId)
                .eq(UploadSession::getUserId, userId)
                .last("limit 1"));
        if (uploadSession == null) {
            throw new BusinessException(ResultCodeEnum.UPLOAD_SESSION_NOT_FOUND);
        }
        return uploadSession;
    }

    private Long requireUserId() {
        Long userId = UserContext.getRequiredUserId();
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        return userId;
    }

    private void markUploadSessionFailed(Long sessionId, String uploadId) {
        UploadSession uploadSession = new UploadSession();
        uploadSession.setId(sessionId);
        uploadSession.setStatus(UploadSessionStatusEnum.FAILED.name());
        uploadSessionMapper.updateById(uploadSession);
        uploadProgressCache.updateStatus(uploadId, UploadSessionStatusEnum.FAILED.name());
    }

    private List<Integer> buildAllChunkIndexes(Integer totalChunks) {
        return java.util.stream.IntStream.range(0, totalChunks)
                .boxed()
                .toList();
    }

    private String resolveContentType(String extension) {
        return switch (StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ROOT) : "") {
            case "pdf" -> "application/pdf";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt", "md", "markdown" -> "text/plain";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
