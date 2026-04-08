package com.studyflow.ai.common.auth;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        LoginRequired methodAnnotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), LoginRequired.class);
        LoginRequired classAnnotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), LoginRequired.class);
        if (methodAnnotation == null && classAnnotation == null) {
            return true;
        }
        String token = jwtTokenProvider.resolveToken(request.getHeader(jwtTokenProvider.getHeaderName()));
        if (token == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        LoginUser loginUser = jwtTokenProvider.parseToken(token);
        User user = userService.getValidUserById(loginUser.getUserId());
        UserContext.set(LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .build());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
