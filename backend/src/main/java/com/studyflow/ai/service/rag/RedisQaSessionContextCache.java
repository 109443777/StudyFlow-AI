package com.studyflow.ai.service.rag;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisQaSessionContextCache implements QaSessionContextCache {

    private static final String KEY_PREFIX = "studyflow:qa:session:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisQaSessionContextCache(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<String> recentHistory(Long sessionId, int limit) {
        Long size = stringRedisTemplate.opsForList().size(buildKey(sessionId));
        if (size == null || size == 0) {
            return List.of();
        }
        long start = Math.max(0L, size - limit);
        List<String> values = stringRedisTemplate.opsForList().range(buildKey(sessionId), start, size - 1);
        return values == null ? List.of() : values;
    }

    @Override
    public void append(Long sessionId, String role, String content, int maxSize) {
        String key = buildKey(sessionId);
        stringRedisTemplate.opsForList().rightPush(key, role + ": " + content);
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size != null && size > maxSize) {
            stringRedisTemplate.opsForList().trim(key, size - maxSize, size - 1);
        }
    }

    private String buildKey(Long sessionId) {
        return KEY_PREFIX + sessionId + ":history";
    }
}
