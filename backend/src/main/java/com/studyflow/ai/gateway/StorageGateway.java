package com.studyflow.ai.gateway;

import java.io.InputStream;
import java.util.List;
import com.studyflow.ai.vo.UploadedPartVO;

public interface StorageGateway {

    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    InputStream download(String objectKey);

    void delete(String objectKey);

    String getFileUrl(String objectKey);

    String initMultipartUpload(String objectKey, String contentType);

    String uploadPart(String objectKey, String storageUploadId, int partNumber, InputStream inputStream, long size, String contentType);

    List<UploadedPartVO> listUploadedParts(String objectKey, String storageUploadId);

    void completeMultipartUpload(String objectKey, String storageUploadId, List<UploadedPartVO> uploadedParts);

    void abortMultipartUpload(String objectKey, String storageUploadId);
}
