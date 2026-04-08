package com.studyflow.ai.gateway;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import io.minio.MinioClient;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(MinioClient.class)
public class FallbackStorageGateway implements StorageGateway {

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }

    @Override
    public String getFileUrl(String objectKey) {
        return null;
    }
}
