package com.studyflow.ai.common.lock;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class RedissonFileAssetLockService implements FileAssetLockService {

    private static final long WAIT_SECONDS = 3L;

    private static final long LEASE_SECONDS = 10L;

    private final RedissonClient redissonClient;

    public RedissonFileAssetLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCodeEnum.CONFLICT, "file asset is being initialized, please retry later");
            }
            return supplier.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "file asset lock interrupted");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
