package com.studyflow.ai.common.ratelimit;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRateLimitService implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean allow(String key, int limit, int windowSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return count != null && count <= limit;
    }
}
