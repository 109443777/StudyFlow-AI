package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String accessToken;

    private String tokenType;

    private Long expiresIn;

    private UserInfoVO userInfo;
}
