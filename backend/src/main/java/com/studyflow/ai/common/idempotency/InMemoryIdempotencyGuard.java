package com.studyflow.ai.common.idempotency;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyGuard implements IdempotencyGuard {

    private final Map<String, Long> holders = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long expireSeconds) {
        cleanupExpired(key);
        long expireAt = Instant.now().plusSeconds(expireSeconds).toEpochMilli();
        return holders.putIfAbsent(key, expireAt) == null;
    }

    @Override
    public void release(String key) {
        holders.remove(key);
    }

    private void cleanupExpired(String key) {
        Long expireAt = holders.get(key);
        if (expireAt != null && expireAt < Instant.now().toEpochMilli()) {
            holders.remove(key, expireAt);
        }
    }
}
