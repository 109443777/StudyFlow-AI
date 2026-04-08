package com.studyflow.ai.gateway;

import java.util.List;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;

public interface AiGateway {

    StudyContentAnalysisResult analyzeStudyContent(AiStudyContentRequest request);

    String answer(String question, List<String> contexts);
}
