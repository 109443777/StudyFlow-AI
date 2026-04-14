package com.studyflow.ai.config;

import com.studyflow.ai.common.lock.FileAssetLockService;
import com.studyflow.ai.common.lock.InMemoryFileAssetLockService;
import com.studyflow.ai.common.lock.RedissonFileAssetLockService;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonLockConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        var singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout(Math.toIntExact(redisProperties.getTimeout().toMillis()));
        }
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public FileAssetLockService redissonFileAssetLockService(RedissonClient redissonClient) {
        return new RedissonFileAssetLockService(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean(FileAssetLockService.class)
    public FileAssetLockService inMemoryFileAssetLockService() {
        return new InMemoryFileAssetLockService();
    }
}
