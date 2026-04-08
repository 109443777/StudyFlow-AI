package com.studyflow.ai.common.idempotency;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisIdempotencyGuard implements IdempotencyGuard {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdempotencyGuard(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, long expireSeconds) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(expireSeconds));
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void release(String key) {
        stringRedisTemplate.delete(key);
    }
}
