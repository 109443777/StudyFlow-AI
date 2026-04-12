package com.studyflow.ai.service.rag;

import java.util.List;

public interface QaSessionContextCache {

    List<String> recentHistory(Long sessionId, int limit);

    void append(Long sessionId, String role, String content, int maxSize);

    void evict(Long sessionId);
}
