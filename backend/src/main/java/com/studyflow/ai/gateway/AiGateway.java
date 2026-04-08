package com.studyflow.ai.gateway;

import java.util.List;

public interface AiGateway {

    String summarize(String content);

    List<String> extractKeywords(String content);

    String answer(String question, List<String> contexts);
}
