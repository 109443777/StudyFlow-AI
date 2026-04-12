package com.studyflow.ai.service.rag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQaSessionContextCache implements QaSessionContextCache {

    private final Map<Long, Deque<String>> cache = new ConcurrentHashMap<>();

    @Override
    public List<String> recentHistory(Long sessionId, int limit) {
        Deque<String> values = cache.get(sessionId);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> history = new ArrayList<>(values);
        int start = Math.max(0, history.size() - limit);
        return history.subList(start, history.size());
    }

    @Override
    public void append(Long sessionId, String role, String content, int maxSize) {
        Deque<String> values = cache.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        synchronized (values) {
            values.addLast(role + ": " + content);
            while (values.size() > maxSize) {
                values.removeFirst();
            }
        }
    }

    @Override
    public void evict(Long sessionId) {
        cache.remove(sessionId);
    }
}
