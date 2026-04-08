package com.studyflow.ai.service;

import com.studyflow.ai.dto.AskQuestionDTO;
import com.studyflow.ai.dto.CreateQaSessionDTO;
import com.studyflow.ai.entity.QaMessage;
import com.studyflow.ai.entity.QaSession;
import com.studyflow.ai.service.rag.ChunkReference;
import com.studyflow.ai.service.rag.QaAnswerResult;
import java.util.List;

public interface RAGQueryService {

    QaSession createSession(Long userId, CreateQaSessionDTO createQaSessionDTO);

    QaAnswerResult ask(Long userId, Long sessionId, AskQuestionDTO askQuestionDTO);

    List<QaMessage> listHistory(Long userId, Long sessionId);

    List<ChunkReference> readReferences(String json);
}
