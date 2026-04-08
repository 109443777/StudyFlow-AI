package com.studyflow.ai.service.vector;

import com.studyflow.ai.entity.MaterialChunk;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkSearchResult {

    private MaterialChunk chunk;

    private Double score;
}
