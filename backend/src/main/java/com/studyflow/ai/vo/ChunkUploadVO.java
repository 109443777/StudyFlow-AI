package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkUploadVO {

    private String uploadId;

    private Integer chunkIndex;

    private Integer uploadedChunkCount;

    private Boolean alreadyUploaded;

    private Boolean completed;
}
