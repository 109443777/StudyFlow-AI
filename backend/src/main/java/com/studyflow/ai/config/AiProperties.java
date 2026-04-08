package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.ai")
public class AiProperties {

    private String provider = "mock";

    private External external = new External();

    @Data
    public static class External {

        private String baseUrl;

        private String apiKey;

        private String model;
    }
}
