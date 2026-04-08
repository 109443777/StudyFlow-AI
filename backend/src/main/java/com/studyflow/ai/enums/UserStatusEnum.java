package com.studyflow.ai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatusEnum {

    DISABLED(0),
    ENABLED(1);

    private final Integer code;
}
