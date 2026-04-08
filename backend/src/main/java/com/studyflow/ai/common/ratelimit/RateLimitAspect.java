package com.studyflow.ai.common.ratelimit;

import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    private final HttpServletRequest httpServletRequest;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identity = resolveIdentity(rateLimit.target());
        String key = "studyflow:rate_limit:" + rateLimit.scene() + ":" + identity;
        boolean allowed = rateLimitService.allow(key, rateLimit.limit(), rateLimit.windowSeconds());
        if (!allowed) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private String resolveIdentity(RateLimitTarget target) {
        Long userId = UserContext.getRequiredUserId();
        String ip = httpServletRequest.getRemoteAddr();
        return switch (target) {
            case USER -> "user:" + userId;
            case IP -> "ip:" + (StringUtils.hasText(ip) ? ip : "unknown");
            case USER_OR_IP -> userId != null ? "user:" + userId : "ip:" + (StringUtils.hasText(ip) ? ip : "unknown");
        };
    }
}
