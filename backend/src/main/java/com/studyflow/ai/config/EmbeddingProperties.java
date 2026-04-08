package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.embedding")
public class EmbeddingProperties {

    private String provider = "mock";

    private Integer mockDimension = 64;

    private External external = new External();

    @Data
    public static class External {

        private String baseUrl;

        private String apiKey;

        private String model;
    }
}
