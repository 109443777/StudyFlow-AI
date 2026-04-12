package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaStreamEventVO {

    private String type;

    private String sessionId;

    private String questionMessageId;

    private String answerMessageId;

    private String content;

    private String answer;

    private String message;

    private List<ChunkReferenceVO> references;
}
