package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadedChunksVO {

    private String uploadId;

    private Integer totalChunks;

    private Integer uploadedChunkCount;

    private List<Integer> uploadedChunks;

    private String status;

    private Boolean completed;
}
