package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.jwt")
public class JwtProperties {

    private String secret;

    private Long expireSeconds;

    private String headerName;

    private String tokenPrefix;
}
