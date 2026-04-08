package com.studyflow.ai.common.response;

import com.studyflow.ai.enums.ResultCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;

    private String message;

    private T data;

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ResultCodeEnum.SUCCESS.getCode())
                .message(ResultCodeEnum.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> failure(ResultCodeEnum resultCodeEnum) {
        return failure(resultCodeEnum, null);
    }

    public static <T> Result<T> failure(ResultCodeEnum resultCodeEnum, T data) {
        return Result.<T>builder()
                .code(resultCodeEnum.getCode())
                .message(resultCodeEnum.getMessage())
                .data(data)
                .build();
    }

    public static <T> Result<T> failure(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .build();
    }
}
