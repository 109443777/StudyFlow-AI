package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaMessageVO {

    private Long id;

    private String role;

    private String content;

    private List<ChunkReferenceVO> referenceChunks;

    private LocalDateTime createTime;
}
