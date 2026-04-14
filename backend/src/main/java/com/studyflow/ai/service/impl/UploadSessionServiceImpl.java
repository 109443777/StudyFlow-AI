package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.lock.FileAssetLockService;
import com.studyflow.ai.common.util.MaterialFileSupport;
import com.studyflow.ai.config.UploadProperties;
import com.studyflow.ai.dto.AbortUploadDTO;
import com.studyflow.ai.dto.CompleteUploadDTO;
import com.studyflow.ai.dto.InitUploadDTO;
import com.studyflow.ai.dto.UploadChunkDTO;
import com.studyflow.ai.entity.FileAsset;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.UploadSession;
import com.studyflow.ai.enums.FileAssetStatusEnum;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.UploadSessionStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.FileAssetMapper;
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
import com.studyflow.ai.vo.UploadedPartVO;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    private final FileAssetMapper fileAssetMapper;

    private final MaterialMapper materialMapper;

    private final MaterialService materialService;

    private final ParseTaskService parseTaskService;

    private final StorageGateway storageGateway;

    private final UploadProgressCache uploadProgressCache;

    private final UploadProperties uploadProperties;

    private final FileAssetLockService fileAssetLockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InitUploadVO initUpload(InitUploadDTO initUploadDTO) {
        Long userId = requireUserId();
        String fileName = initUploadDTO.getFileName();
        String extension = MaterialFileSupport.extractExtension(fileName);
        MaterialTypeEnum materialTypeEnum = MaterialFileSupport.resolveMaterialType(fileName);
        String fileSha256 = initUploadDTO.getFileSha256().toLowerCase(Locale.ROOT);
        String lockKey = buildFileAssetLockKey(fileSha256, initUploadDTO.getFileSize());
        return fileAssetLockService.executeWithLock(lockKey,
                () -> initUploadWithFileAsset(userId, initUploadDTO, fileName, extension, materialTypeEnum, fileSha256));
    }

    private InitUploadVO initUploadWithFileAsset(
            Long userId,
            InitUploadDTO initUploadDTO,
            String fileName,
            String extension,
            MaterialTypeEnum materialTypeEnum,
            String fileSha256) {
        FileAsset existingAsset = findFileAsset(fileSha256, initUploadDTO.getFileSize());
        if (existingAsset != null && FileAssetStatusEnum.UPLOADED.name().equals(existingAsset.getAssetStatus())) {
            Material material = createReusedMaterial(userId, fileName, extension, initUploadDTO.getFileSize(),
                    materialTypeEnum, existingAsset);
            return InitUploadVO.builder()
                    .uploadId(null)
                    .materialId(material.getId())
                    .fileAssetId(existingAsset.getId())
                    .fileSha256(fileSha256)
                    .partSize(0L)
                    .totalParts(0)
                    .status(UploadSessionStatusEnum.COMPLETED.name())
                    .uploadRequired(false)
                    .assetReused(true)
                    .build();
        }
        if (existingAsset != null && !FileAssetStatusEnum.FAILED.name().equals(existingAsset.getAssetStatus())) {
            throw new BusinessException(ResultCodeEnum.CONFLICT, "same file asset is being uploaded, please retry later");
        }

        long partSize = resolvePartSize(initUploadDTO.getFileSize());
        int totalParts = Math.toIntExact((initUploadDTO.getFileSize() + partSize - 1) / partSize);

        FileAsset fileAsset = existingAsset == null
                ? createFileAsset(fileName, extension, initUploadDTO.getFileSize(), materialTypeEnum, fileSha256)
                : resetFailedFileAsset(existingAsset, fileName, extension, materialTypeEnum);

        Material material = new Material();
        material.setUserId(userId);
        material.setFileAssetId(fileAsset.getId());
        material.setFileSha256(fileSha256);
        material.setFileName(fileName);
        material.setFileType(extension);
        material.setFileSize(initUploadDTO.getFileSize());
        material.setObjectKey(fileAsset.getObjectKey());
        material.setMaterialType(materialTypeEnum.name());
        material.setParseStatus(MaterialParseStatusEnum.INIT.name());
        material.setUploadStatus(MaterialUploadStatusEnum.INIT.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String storageUploadId = storageGateway.initMultipartUpload(material.getObjectKey(), resolveContentType(extension));

        UploadSession uploadSession = new UploadSession();
        uploadSession.setUploadId(uploadId);
        uploadSession.setStorageUploadId(storageUploadId);
        uploadSession.setMaterialId(material.getId());
        uploadSession.setUserId(userId);
        uploadSession.setFileName(fileName);
        uploadSession.setFileType(extension);
        uploadSession.setFileSize(initUploadDTO.getFileSize());
        uploadSession.setFileMd5(initUploadDTO.getFileMd5().toLowerCase(Locale.ROOT));
        uploadSession.setFileSha256(fileSha256);
        uploadSession.setPartSize(partSize);
        uploadSession.setTotalParts(totalParts);
        uploadSession.setUploadedParts(0);
        uploadSession.setObjectKey(material.getObjectKey());
        uploadSession.setMaterialType(materialTypeEnum.name());
        uploadSession.setStatus(UploadSessionStatusEnum.INIT.name());
        uploadSession.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        uploadSession.setExpireTime(LocalDateTime.now().plusHours(uploadProperties.getSessionExpireHours()));
        uploadSessionMapper.insert(uploadSession);

        uploadProgressCache.initSession(
                uploadId,
                partSize,
                totalParts,
                uploadSession.getFileMd5(),
                UploadSessionStatusEnum.INIT.name(),
                material.getId(),
                userId,
                storageUploadId);

        return InitUploadVO.builder()
                .uploadId(uploadId)
                .materialId(material.getId())
                .fileAssetId(fileAsset.getId())
                .fileSha256(fileSha256)
                .partSize(partSize)
                .totalParts(totalParts)
                .status(uploadSession.getStatus())
                .uploadRequired(true)
                .assetReused(false)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChunkUploadVO uploadPart(UploadChunkDTO uploadChunkDTO) {
        Long userId = requireUserId();
        UploadSession uploadSession = getSession(uploadChunkDTO.getUploadId(), userId);
        Integer partNumber = uploadChunkDTO.getPartNumber();
        if (partNumber < 1 || partNumber > uploadSession.getTotalParts()) {
            throw new BusinessException(ResultCodeEnum.PART_NUMBER_INVALID);
        }
        ensureUploadSessionActive(uploadSession);

        Optional<String> existingEtag = uploadProgressCache.getUploadedPartEtag(uploadSession.getUploadId(), partNumber);
        if (existingEtag.isPresent()) {
            return ChunkUploadVO.builder()
                    .uploadId(uploadSession.getUploadId())
                    .partNumber(partNumber)
                    .etag(existingEtag.get())
                    .uploadedPartCount(Math.max(uploadSession.getUploadedParts(), uploadProgressCache.getUploadedParts(uploadSession.getUploadId()).size()))
                    .alreadyUploaded(true)
                    .completed(UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus()))
                    .build();
        }

        MultipartFile part = uploadChunkDTO.getPart();
        if (part == null || part.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.FILE_EMPTY);
        }

        try (InputStream inputStream = part.getInputStream()) {
            String etag = storageGateway.uploadPart(
                    uploadSession.getObjectKey(),
                    uploadSession.getStorageUploadId(),
                    partNumber,
                    inputStream,
                    part.getSize(),
                    part.getContentType());
            Integer uploadedPartCount = uploadProgressCache.saveUploadedPart(uploadSession.getUploadId(), partNumber, etag);
            uploadProgressCache.updateStatus(uploadSession.getUploadId(), UploadSessionStatusEnum.UPLOADING.name());
            updateUploadSessionProgress(uploadSession.getId(), uploadedPartCount, UploadSessionStatusEnum.UPLOADING.name(), null);

            return ChunkUploadVO.builder()
                    .uploadId(uploadSession.getUploadId())
                    .partNumber(partNumber)
                    .etag(etag)
                    .uploadedPartCount(uploadedPartCount)
                    .alreadyUploaded(false)
                    .completed(uploadedPartCount.equals(uploadSession.getTotalParts()))
                    .build();
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to read upload part");
        } catch (BusinessException exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId(), "failed to upload multipart part");
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to upload multipart part");
        }
    }

    @Override
    public UploadedChunksVO listUploadedParts(String uploadId) {
        Long userId = requireUserId();
        UploadSession uploadSession = getSession(uploadId, userId);
        List<UploadedPartVO> uploadedParts = recoverUploadedParts(uploadSession);
        return UploadedChunksVO.builder()
                .uploadId(uploadId)
                .totalParts(uploadSession.getTotalParts())
                .uploadedPartCount(uploadedParts.size())
                .uploadedParts(uploadedParts)
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
        ensureUploadSessionActive(uploadSession);

        List<UploadedPartVO> uploadedParts = recoverUploadedParts(uploadSession);
        validateUploadedParts(uploadSession, uploadedParts);

        try {
            updateUploadSessionProgress(uploadSession.getId(), uploadedParts.size(), UploadSessionStatusEnum.COMPLETING.name(), null);
            uploadProgressCache.updateStatus(uploadSession.getUploadId(), UploadSessionStatusEnum.COMPLETING.name());
            storageGateway.completeMultipartUpload(uploadSession.getObjectKey(), uploadSession.getStorageUploadId(), uploadedParts);

            Material material = new Material();
            material.setId(uploadSession.getMaterialId());
            material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
            material.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
            materialMapper.updateById(material);
            material = materialMapper.selectById(uploadSession.getMaterialId());
            markFileAssetUploaded(material);
            log.info("Multipart upload completed successfully, uploadId={}, materialId={}, totalParts={}",
                    uploadSession.getUploadId(), uploadSession.getMaterialId(), uploadSession.getTotalParts());
            parseTaskService.createAndDispatchInitialTask(material);

            updateUploadSessionProgress(uploadSession.getId(), uploadedParts.size(), UploadSessionStatusEnum.COMPLETED.name(), null);
            uploadProgressCache.clear(uploadSession.getUploadId());

            return materialService.getMaterialDetail(uploadSession.getMaterialId());
        } catch (BusinessException exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId(), "failed to complete multipart upload");
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to complete multipart upload");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abortUpload(AbortUploadDTO abortUploadDTO) {
        Long userId = requireUserId();
        UploadSession uploadSession = getSession(abortUploadDTO.getUploadId(), userId);
        if (UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus())
                || UploadSessionStatusEnum.ABORTED.name().equals(uploadSession.getStatus())
                || UploadSessionStatusEnum.EXPIRED.name().equals(uploadSession.getStatus())) {
            return;
        }
        try {
            storageGateway.abortMultipartUpload(uploadSession.getObjectKey(), uploadSession.getStorageUploadId());
            updateUploadSessionProgress(uploadSession.getId(), uploadSession.getUploadedParts(), UploadSessionStatusEnum.ABORTED.name(), null);
            uploadProgressCache.clear(uploadSession.getUploadId());

            Material material = new Material();
            material.setId(uploadSession.getMaterialId());
            material.setUploadStatus(MaterialUploadStatusEnum.FAILED.name());
            materialMapper.updateById(material);
            markFileAssetFailed(uploadSession.getMaterialId());
        } catch (BusinessException exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            markUploadSessionFailed(uploadSession.getId(), uploadSession.getUploadId(), "failed to abort multipart upload");
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to abort multipart upload");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abortExpiredUploads() {
        List<UploadSession> expiredSessions = uploadSessionMapper.selectList(new LambdaQueryWrapper<UploadSession>()
                .lt(UploadSession::getExpireTime, LocalDateTime.now())
                .in(UploadSession::getStatus,
                        UploadSessionStatusEnum.INIT.name(),
                        UploadSessionStatusEnum.UPLOADING.name(),
                        UploadSessionStatusEnum.COMPLETING.name()));
        for (UploadSession uploadSession : expiredSessions) {
            try {
                storageGateway.abortMultipartUpload(uploadSession.getObjectKey(), uploadSession.getStorageUploadId());
            } catch (Exception exception) {
                log.warn("Failed to abort expired multipart upload on storage, uploadId={}, storageUploadId={}",
                        uploadSession.getUploadId(), uploadSession.getStorageUploadId(), exception);
            }
            updateUploadSessionProgress(uploadSession.getId(), uploadSession.getUploadedParts(), UploadSessionStatusEnum.EXPIRED.name(), "upload session expired");
            uploadProgressCache.clear(uploadSession.getUploadId());

            Material material = new Material();
            material.setId(uploadSession.getMaterialId());
            material.setUploadStatus(MaterialUploadStatusEnum.FAILED.name());
            materialMapper.updateById(material);
            markFileAssetFailed(uploadSession.getMaterialId());
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

    private long resolvePartSize(Long fileSize) {
        long configuredPartSize = Math.max(uploadProperties.getPartSizeBytes(), 5L * 1024 * 1024);
        return Math.min(configuredPartSize, Math.max(fileSize, configuredPartSize));
    }

    private void ensureUploadSessionActive(UploadSession uploadSession) {
        if (UploadSessionStatusEnum.COMPLETED.name().equals(uploadSession.getStatus())) {
            throw new BusinessException(ResultCodeEnum.CONFLICT, "upload session has already completed");
        }
        if (UploadSessionStatusEnum.ABORTED.name().equals(uploadSession.getStatus())) {
            throw new BusinessException(ResultCodeEnum.CONFLICT, "upload session has been aborted");
        }
        if (UploadSessionStatusEnum.EXPIRED.name().equals(uploadSession.getStatus())) {
            throw new BusinessException(ResultCodeEnum.CONFLICT, "upload session has expired");
        }
        if (UploadSessionStatusEnum.FAILED.name().equals(uploadSession.getStatus())) {
            throw new BusinessException(ResultCodeEnum.CONFLICT, "upload session has failed");
        }
    }

    private List<UploadedPartVO> recoverUploadedParts(UploadSession uploadSession) {
        List<UploadedPartVO> cachedParts = uploadProgressCache.getUploadedParts(uploadSession.getUploadId());
        if (!cachedParts.isEmpty()) {
            return cachedParts.stream()
                    .sorted(Comparator.comparing(UploadedPartVO::getPartNumber))
                    .toList();
        }
        List<UploadedPartVO> storageParts = storageGateway.listUploadedParts(uploadSession.getObjectKey(), uploadSession.getStorageUploadId());
        for (UploadedPartVO uploadedPart : storageParts) {
            uploadProgressCache.saveUploadedPart(uploadSession.getUploadId(), uploadedPart.getPartNumber(), uploadedPart.getEtag());
        }
        return storageParts.stream()
                .sorted(Comparator.comparing(UploadedPartVO::getPartNumber))
                .toList();
    }

    private void validateUploadedParts(UploadSession uploadSession, List<UploadedPartVO> uploadedParts) {
        if (uploadedParts.size() != uploadSession.getTotalParts()) {
            throw new BusinessException(ResultCodeEnum.UPLOAD_NOT_COMPLETE);
        }
        for (int index = 0; index < uploadedParts.size(); index++) {
            int expectedPartNumber = index + 1;
            UploadedPartVO uploadedPart = uploadedParts.get(index);
            if (uploadedPart.getPartNumber() == null
                    || uploadedPart.getPartNumber() != expectedPartNumber
                    || !StringUtils.hasText(uploadedPart.getEtag())) {
                throw new BusinessException(ResultCodeEnum.UPLOAD_NOT_COMPLETE, "uploaded parts are incomplete");
            }
        }
    }

    private void updateUploadSessionProgress(Long sessionId, Integer uploadedPartCount, String status, String failReason) {
        UploadSession uploadSession = new UploadSession();
        uploadSession.setId(sessionId);
        uploadSession.setUploadedParts(uploadedPartCount);
        uploadSession.setStatus(status);
        uploadSession.setFailReason(failReason);
        uploadSessionMapper.updateById(uploadSession);
    }

    private void markUploadSessionFailed(Long sessionId, String uploadId, String failReason) {
        updateUploadSessionProgress(sessionId, null, UploadSessionStatusEnum.FAILED.name(), failReason);
        uploadProgressCache.updateStatus(uploadId, UploadSessionStatusEnum.FAILED.name());
        UploadSession uploadSession = uploadSessionMapper.selectById(sessionId);
        if (uploadSession != null) {
            markFileAssetFailed(uploadSession.getMaterialId());
        }
    }

    private FileAsset findFileAsset(String fileSha256, Long fileSize) {
        return fileAssetMapper.selectOne(new LambdaQueryWrapper<FileAsset>()
                .eq(FileAsset::getFileSha256, fileSha256)
                .eq(FileAsset::getFileSize, fileSize)
                .last("limit 1"));
    }

    private FileAsset createFileAsset(
            String fileName,
            String extension,
            Long fileSize,
            MaterialTypeEnum materialTypeEnum,
            String fileSha256) {
        FileAsset fileAsset = new FileAsset();
        fileAsset.setFileSha256(fileSha256);
        fileAsset.setFileName(fileName);
        fileAsset.setFileType(extension);
        fileAsset.setFileSize(fileSize);
        fileAsset.setObjectKey(MaterialFileSupport.buildFileAssetObjectKey(fileSha256, extension));
        fileAsset.setMaterialType(materialTypeEnum.name());
        fileAsset.setAssetStatus(FileAssetStatusEnum.UPLOADING.name());
        fileAsset.setParseStatus(MaterialParseStatusEnum.INIT.name());
        fileAssetMapper.insert(fileAsset);
        return fileAsset;
    }

    private FileAsset resetFailedFileAsset(
            FileAsset fileAsset,
            String fileName,
            String extension,
            MaterialTypeEnum materialTypeEnum) {
        fileAsset.setFileName(fileName);
        fileAsset.setFileType(extension);
        fileAsset.setObjectKey(MaterialFileSupport.buildFileAssetObjectKey(fileAsset.getFileSha256(), extension));
        fileAsset.setMaterialType(materialTypeEnum.name());
        fileAsset.setCanonicalMaterialId(null);
        fileAsset.setAssetStatus(FileAssetStatusEnum.UPLOADING.name());
        fileAsset.setParseStatus(MaterialParseStatusEnum.INIT.name());
        fileAssetMapper.updateById(fileAsset);
        return fileAssetMapper.selectById(fileAsset.getId());
    }

    private Material createReusedMaterial(
            Long userId,
            String fileName,
            String extension,
            Long fileSize,
            MaterialTypeEnum materialTypeEnum,
            FileAsset fileAsset) {
        Material material = new Material();
        material.setUserId(userId);
        material.setFileAssetId(fileAsset.getId());
        material.setReuseSourceMaterialId(fileAsset.getCanonicalMaterialId());
        material.setFileSha256(fileAsset.getFileSha256());
        material.setFileName(fileName);
        material.setFileType(extension);
        material.setFileSize(fileSize);
        material.setObjectKey(fileAsset.getObjectKey());
        material.setMaterialType(materialTypeEnum.name());
        material.setParseStatus(fileAsset.getParseStatus());
        material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);
        return material;
    }

    private void markFileAssetUploaded(Material material) {
        if (material.getFileAssetId() == null) {
            return;
        }
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(material.getFileAssetId());
        fileAsset.setCanonicalMaterialId(material.getId());
        fileAsset.setAssetStatus(FileAssetStatusEnum.UPLOADED.name());
        fileAsset.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
        fileAssetMapper.updateById(fileAsset);
    }

    private void markFileAssetFailed(Long materialId) {
        Material material = materialMapper.selectById(materialId);
        if (material == null || material.getFileAssetId() == null) {
            return;
        }
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(material.getFileAssetId());
        fileAsset.setAssetStatus(FileAssetStatusEnum.FAILED.name());
        fileAsset.setParseStatus(MaterialParseStatusEnum.FAILED.name());
        fileAssetMapper.updateById(fileAsset);
    }

    private String buildFileAssetLockKey(String fileSha256, Long fileSize) {
        return "studyflow:lock:file_asset:" + fileSha256 + ":" + fileSize;
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
