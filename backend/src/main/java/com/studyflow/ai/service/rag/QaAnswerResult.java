package com.studyflow.ai.service.rag;

import com.studyflow.ai.entity.QaMessage;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaAnswerResult {

    private Long sessionId;

    private QaMessage questionMessage;

    private QaMessage answerMessage;

    private List<ChunkReference> references;
}
