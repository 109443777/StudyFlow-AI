package com.studyflow.ai.gateway;

import java.util.List;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;

public interface AiGateway {

    StudyContentAnalysisResult analyzeStudyContent(AiStudyContentRequest request);

    String answer(String question, List<String> contexts);

    default void streamAnswer(String question, List<String> contexts, AiAnswerStreamHandler streamHandler) {
        try {
            String answer = answer(question, contexts);
            if (answer != null && !answer.isBlank()) {
                streamHandler.onNext(answer);
            }
            streamHandler.onComplete();
        } catch (RuntimeException exception) {
            streamHandler.onError(exception);
        }
    }
}
