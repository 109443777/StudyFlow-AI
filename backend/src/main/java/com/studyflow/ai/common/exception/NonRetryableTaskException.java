package com.studyflow.ai.common.exception;

import com.studyflow.ai.enums.ResultCodeEnum;

public class NonRetryableTaskException extends BusinessException {

    public NonRetryableTaskException(String message) {
        super(ResultCodeEnum.SYSTEM_BUSY, message);
    }
}
