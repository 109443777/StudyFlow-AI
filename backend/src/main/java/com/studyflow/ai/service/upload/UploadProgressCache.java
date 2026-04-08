package com.studyflow.ai.service.upload;

import java.util.List;

public interface UploadProgressCache {

    void initSession(String uploadId, Integer totalChunks, String fileMd5, String status, Long materialId, Long userId);

    boolean isChunkUploaded(String uploadId, Integer chunkIndex);

    Integer markChunkUploaded(String uploadId, Integer chunkIndex);

    List<Integer> getUploadedChunks(String uploadId);

    void updateStatus(String uploadId, String status);
}
