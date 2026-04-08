package com.studyflow.ai.common.idempotency;

public interface IdempotencyGuard {

    boolean tryAcquire(String key, long expireSeconds);

    void release(String key);
}
