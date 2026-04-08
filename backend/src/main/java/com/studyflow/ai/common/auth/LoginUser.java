package com.studyflow.ai.common.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginUser {

    private Long userId;

    private String username;
}
