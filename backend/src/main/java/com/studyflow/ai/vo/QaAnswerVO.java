package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaAnswerVO {

    private Long sessionId;

    private Long questionMessageId;

    private Long answerMessageId;

    private String answer;

    private List<ChunkReferenceVO> references;
}
