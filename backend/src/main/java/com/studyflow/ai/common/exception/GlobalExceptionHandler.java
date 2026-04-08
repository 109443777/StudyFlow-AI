package com.studyflow.ai.common.exception;

import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.enums.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        log.warn("Business exception on [{} {}]: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return Result.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, MissingServletRequestParameterException.class})
    public Result<Void> handleValidationException(Exception exception, HttpServletRequest request) {
        log.warn("Validation exception on [{} {}]: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return Result.failure(ResultCodeEnum.BAD_REQUEST.getCode(), ResultCodeEnum.BAD_REQUEST.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on [{} {}]", request.getMethod(), request.getRequestURI(), exception);
        return Result.failure(ResultCodeEnum.INTERNAL_ERROR.getCode(), ResultCodeEnum.INTERNAL_ERROR.getMessage());
    }
}
