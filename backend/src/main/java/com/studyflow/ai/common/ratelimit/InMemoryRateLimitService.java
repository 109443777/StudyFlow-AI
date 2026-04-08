package com.studyflow.ai.common.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRateLimitService implements RateLimitService {

    private final Map<String, CounterWindow> windows = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, int limit, int windowSeconds) {
        long now = Instant.now().toEpochMilli();
        long expireAt = now + windowSeconds * 1000L;
        CounterWindow counterWindow = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expireAt < now) {
                return new CounterWindow(1, expireAt);
            }
            existing.count += 1;
            return existing;
        });
        return counterWindow.count <= limit;
    }

    private static class CounterWindow {

        private int count;

        private final long expireAt;

        private CounterWindow(int count, long expireAt) {
            this.count = count;
            this.expireAt = expireAt;
        }
    }
}
