package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.langchain4j")
public class LangChainProperties {

    private String chatModel;

    private String embeddingModel;

    private String apiKey;

    private String baseUrl;
}
