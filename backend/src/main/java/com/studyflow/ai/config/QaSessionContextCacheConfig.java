package com.studyflow.ai.config;

import com.studyflow.ai.service.rag.InMemoryQaSessionContextCache;
import com.studyflow.ai.service.rag.QaSessionContextCache;
import com.studyflow.ai.service.rag.RedisQaSessionContextCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class QaSessionContextCacheConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public QaSessionContextCache redisQaSessionContextCache(StringRedisTemplate stringRedisTemplate) {
        return new RedisQaSessionContextCache(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(QaSessionContextCache.class)
    public QaSessionContextCache inMemoryQaSessionContextCache() {
        return new InMemoryQaSessionContextCache();
    }
}
