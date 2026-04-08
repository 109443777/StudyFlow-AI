package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkReferenceVO {

    private Long chunkId;

    private Integer chunkIndex;

    private Double score;

    private String chunkText;
}
