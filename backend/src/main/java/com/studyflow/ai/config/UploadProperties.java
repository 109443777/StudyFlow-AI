package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.upload")
public class UploadProperties {

    private Long sessionExpireHours = 24L;
}
