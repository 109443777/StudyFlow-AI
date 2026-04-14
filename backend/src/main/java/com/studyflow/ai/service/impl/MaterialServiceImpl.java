package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.dto.MaterialQueryDTO;
import com.studyflow.ai.dto.MaterialUploadDTO;
import com.studyflow.ai.entity.FileAsset;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.FileAssetMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.service.MaterialService;
import com.studyflow.ai.service.ParseTaskService;
import com.studyflow.ai.vo.MaterialVO;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;

    private final FileAssetMapper fileAssetMapper;

    private final StorageGateway storageGateway;

    private final ParseTaskService parseTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialVO uploadMaterial(MaterialUploadDTO materialUploadDTO) {
        MultipartFile file = materialUploadDTO.getFile();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.FILE_EMPTY);
        }
        Long userId = UserContext.getRequiredUserId();
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        MaterialTypeEnum materialTypeEnum = MaterialTypeEnum.fromExtension(extension);
        if (materialTypeEnum == null) {
            throw new BusinessException(ResultCodeEnum.UNSUPPORTED_FILE_TYPE);
        }
        Material material = new Material();
        material.setUserId(userId);
        material.setFileName(originalFilename);
        material.setFileType(extension);
        material.setFileSize(file.getSize());
        material.setObjectKey(buildObjectKey(userId, extension));
        material.setMaterialType(materialTypeEnum.name());
        material.setParseStatus(MaterialParseStatusEnum.INIT.name());
        material.setUploadStatus(MaterialUploadStatusEnum.INIT.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);
        try (InputStream inputStream = file.getInputStream()) {
            storageGateway.upload(material.getObjectKey(), inputStream, file.getSize(), file.getContentType());
            material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
            material.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
            materialMapper.updateById(material);
            log.info("Material upload succeeded, materialId={}, userId={}, fileName={}, materialType={}",
                    material.getId(), userId, originalFilename, material.getMaterialType());
            parseTaskService.createAndDispatchInitialTask(material);
            return toMaterialVO(material);
        } catch (IOException exception) {
            markUploadFailed(material.getId());
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to read upload file");
        } catch (BusinessException exception) {
            markUploadFailed(material.getId());
            throw exception;
        } catch (Exception exception) {
            markUploadFailed(material.getId());
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to upload material");
        }
    }

    @Override
    public MaterialVO getMaterialDetail(Long materialId) {
        Long userId = UserContext.getRequiredUserId();
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialId)
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        return toMaterialVO(material);
    }

    @Override
    public List<MaterialVO> listMyMaterials(MaterialQueryDTO materialQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        LambdaQueryWrapper<Material> queryWrapper = new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, userId)
                .orderByDesc(Material::getCreateTime);
        if (StringUtils.hasText(materialQueryDTO.getMaterialType())) {
            queryWrapper.eq(Material::getMaterialType, materialQueryDTO.getMaterialType().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(materialQueryDTO.getParseStatus())) {
            queryWrapper.eq(Material::getParseStatus, materialQueryDTO.getParseStatus().toUpperCase(Locale.ROOT));
        }
        return materialMapper.selectList(queryWrapper)
                .stream()
                .map(this::toMaterialVO)
                .collect(Collectors.toList());
    }

    private void markUploadFailed(Long materialId) {
        Material failedMaterial = new Material();
        failedMaterial.setId(materialId);
        failedMaterial.setUploadStatus(MaterialUploadStatusEnum.FAILED.name());
        materialMapper.updateById(failedMaterial);
    }

    private MaterialVO toMaterialVO(Material material) {
        String parseStatus = resolveParseStatus(material);
        return MaterialVO.builder()
                .id(material.getId())
                .userId(material.getUserId())
                .fileAssetId(material.getFileAssetId())
                .reuseSourceMaterialId(material.getReuseSourceMaterialId())
                .fileSha256(material.getFileSha256())
                .fileName(material.getFileName())
                .fileType(material.getFileType())
                .fileSize(material.getFileSize())
                .objectKey(material.getObjectKey())
                .fileUrl(storageGateway.getFileUrl(material.getObjectKey()))
                .materialType(material.getMaterialType())
                .parseStatus(parseStatus)
                .uploadStatus(material.getUploadStatus())
                .sourceType(material.getSourceType())
                .createTime(material.getCreateTime())
                .updateTime(material.getUpdateTime())
                .build();
    }

    private String resolveParseStatus(Material material) {
        if (material.getFileAssetId() == null) {
            return material.getParseStatus();
        }
        FileAsset fileAsset = fileAssetMapper.selectById(material.getFileAssetId());
        if (fileAsset != null && StringUtils.hasText(fileAsset.getParseStatus())) {
            return fileAsset.getParseStatus();
        }
        return material.getParseStatus();
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new BusinessException(ResultCodeEnum.UNSUPPORTED_FILE_TYPE);
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String buildObjectKey(Long userId, String extension) {
        return "materials/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }
}
