package com.studyflow.ai.common.util;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;

public final class MaterialFileSupport {

    private MaterialFileSupport() {
    }

    public static String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new BusinessException(ResultCodeEnum.UNSUPPORTED_FILE_TYPE);
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static MaterialTypeEnum resolveMaterialType(String fileName) {
        MaterialTypeEnum materialTypeEnum = MaterialTypeEnum.fromExtension(extractExtension(fileName));
        if (materialTypeEnum == null) {
            throw new BusinessException(ResultCodeEnum.UNSUPPORTED_FILE_TYPE);
        }
        return materialTypeEnum;
    }

    public static String buildMaterialObjectKey(Long userId, String extension) {
        return "materials/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    public static String buildChunkObjectKey(String uploadId, Integer chunkIndex) {
        return "upload-sessions/" + uploadId + "/chunks/" + chunkIndex + ".part";
    }
}
