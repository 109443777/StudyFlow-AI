package com.studyflow.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        MinioProperties.class,
        LangChainProperties.class,
        AiProperties.class,
        JwtProperties.class,
        UploadProperties.class,
        TranscriptionProperties.class
})
public class InfrastructurePropertiesConfig {
}
