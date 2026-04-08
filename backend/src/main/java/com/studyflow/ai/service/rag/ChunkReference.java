package com.studyflow.ai.service.rag;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkReference {

    private Long chunkId;

    private Integer chunkIndex;

    private Double score;

    private String chunkText;
}
