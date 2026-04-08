package com.studyflow.ai.config;

import com.studyflow.ai.common.idempotency.IdempotencyGuard;
import com.studyflow.ai.common.idempotency.InMemoryIdempotencyGuard;
import com.studyflow.ai.common.idempotency.RedisIdempotencyGuard;
import com.studyflow.ai.common.ratelimit.InMemoryRateLimitService;
import com.studyflow.ai.common.ratelimit.RateLimitService;
import com.studyflow.ai.common.ratelimit.RedisRateLimitService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GovernanceConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotencyGuard redisIdempotencyGuard(StringRedisTemplate stringRedisTemplate) {
        return new RedisIdempotencyGuard(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    public IdempotencyGuard inMemoryIdempotencyGuard() {
        return new InMemoryIdempotencyGuard();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RateLimitService redisRateLimitService(StringRedisTemplate stringRedisTemplate) {
        return new RedisRateLimitService(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitService.class)
    public RateLimitService inMemoryRateLimitService() {
        return new InMemoryRateLimitService();
    }
}
