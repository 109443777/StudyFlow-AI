package com.studyflow.ai.service.upload;

import com.studyflow.ai.vo.UploadedPartVO;
import java.util.List;
import java.util.Optional;

public interface UploadProgressCache {

    void initSession(String uploadId, Long partSize, Integer totalParts, String fileMd5, String status, Long materialId, Long userId, String storageUploadId);

    Optional<String> getUploadedPartEtag(String uploadId, Integer partNumber);

    Integer saveUploadedPart(String uploadId, Integer partNumber, String etag);

    List<UploadedPartVO> getUploadedParts(String uploadId);

    void updateStatus(String uploadId, String status);

    void clear(String uploadId);
}
