package com.studyflow.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResultCodeEnum {

    SUCCESS(0, "success"),
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或鉴权失败"),
    FORBIDDEN(40300, "无权限访问"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源冲突"),
    INTERNAL_ERROR(50000, "系统内部错误"),
    SYSTEM_BUSY(50001, "系统繁忙，请稍后重试");

    private final Integer code;

    private final String message;
}
