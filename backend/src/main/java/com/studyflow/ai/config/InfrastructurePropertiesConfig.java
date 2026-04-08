package com.studyflow.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        MinioProperties.class,
        LangChainProperties.class,
        AiProperties.class,
        EmbeddingProperties.class,
        JwtProperties.class,
        RagProperties.class,
        UploadProperties.class,
        TranscriptionProperties.class
})
public class InfrastructurePropertiesConfig {
}
