package com.studyflow.ai.config;

import com.studyflow.ai.gateway.EmbeddingGateway;
import com.studyflow.ai.gateway.ExternalEmbeddingGateway;
import com.studyflow.ai.gateway.MockEmbeddingGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "studyflow.embedding.provider", havingValue = "mock", matchIfMissing = true)
    public EmbeddingGateway mockEmbeddingGateway(EmbeddingProperties embeddingProperties) {
        return new MockEmbeddingGateway(embeddingProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "studyflow.embedding.provider", havingValue = "external")
    public EmbeddingGateway externalEmbeddingGateway(EmbeddingProperties embeddingProperties) {
        return new ExternalEmbeddingGateway(embeddingProperties);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingGateway.class)
    public EmbeddingGateway fallbackEmbeddingGateway(EmbeddingProperties embeddingProperties) {
        return new MockEmbeddingGateway(embeddingProperties);
    }
}
