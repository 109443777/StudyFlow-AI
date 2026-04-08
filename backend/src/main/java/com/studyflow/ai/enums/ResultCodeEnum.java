package com.studyflow.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResultCodeEnum {

    SUCCESS(0, "success"),
    BAD_REQUEST(40000, "bad request"),
    USERNAME_ALREADY_EXISTS(40001, "username already exists"),
    UNAUTHORIZED(40100, "unauthorized"),
    INVALID_CREDENTIALS(40101, "invalid username or password"),
    TOKEN_INVALID(40102, "invalid or expired token"),
    FORBIDDEN(40300, "forbidden"),
    USER_DISABLED(40301, "user has been disabled"),
    NOT_FOUND(40400, "resource not found"),
    CONFLICT(40900, "resource conflict"),
    INTERNAL_ERROR(50000, "internal server error"),
    SYSTEM_BUSY(50001, "system busy, please retry later");

    private final Integer code;

    private final String message;
}
