package com.studyflow.ai.common.exception;

import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.enums.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
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
        String message = ResultCodeEnum.BAD_REQUEST.getMessage();
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            message = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .findFirst()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .orElse(message);
        } else if (exception instanceof BindException bindException) {
            message = bindException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .findFirst()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .orElse(message);
        }
        return Result.failure(ResultCodeEnum.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException exception, HttpServletRequest request) {
        log.warn("Constraint violation on [{} {}]: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return Result.failure(ResultCodeEnum.BAD_REQUEST.getCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on [{} {}]", request.getMethod(), request.getRequestURI(), exception);
        return Result.failure(ResultCodeEnum.INTERNAL_ERROR.getCode(), ResultCodeEnum.INTERNAL_ERROR.getMessage());
    }
}
