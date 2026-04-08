package com.studyflow.ai.common.ratelimit;

public interface RateLimitService {

    boolean allow(String key, int limit, int windowSeconds);
}
