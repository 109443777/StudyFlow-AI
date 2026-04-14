package com.studyflow.ai.common.lock;

import java.util.function.Supplier;

public interface FileAssetLockService {

    <T> T executeWithLock(String lockKey, Supplier<T> supplier);
}
