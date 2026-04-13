package com.studyflow.ai.gateway;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.vo.UploadedPartVO;
import io.minio.MinioClient;
import java.io.InputStream;
import java.util.List;
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
    public InputStream download(String objectKey) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }

    @Override
    public void delete(String objectKey) {
    }

    @Override
    public String getFileUrl(String objectKey) {
        return null;
    }

    @Override
    public String initMultipartUpload(String objectKey, String contentType) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }

    @Override
    public String uploadPart(String objectKey, String storageUploadId, int partNumber, InputStream inputStream, long size, String contentType) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }

    @Override
    public List<UploadedPartVO> listUploadedParts(String objectKey, String storageUploadId) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }

    @Override
    public void completeMultipartUpload(String objectKey, String storageUploadId, List<UploadedPartVO> uploadedParts) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }

    @Override
    public void abortMultipartUpload(String objectKey, String storageUploadId) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "storage gateway is not configured");
    }
}
