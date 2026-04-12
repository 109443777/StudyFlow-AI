package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "studyflow.langchain4j")
public class LangChainProperties {

    private String chatModel;

    private String embeddingModel;

    private String apiKey;

    private String baseUrl;

    private Duration timeout = Duration.ofSeconds(30);

    private Integer maxRetries = 2;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    private Integer embeddingBatchSize = 10;
}
