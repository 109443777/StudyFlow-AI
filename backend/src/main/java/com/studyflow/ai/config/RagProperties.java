package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.rag")
public class RagProperties {

    private Integer chunkSize = 800;

    private Integer chunkOverlap = 120;

    private Integer topK = 4;

    private Integer historySize = 6;
}
