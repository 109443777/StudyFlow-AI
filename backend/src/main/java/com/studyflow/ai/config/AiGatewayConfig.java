package com.studyflow.ai.config;

import com.studyflow.ai.gateway.AiGateway;
import com.studyflow.ai.gateway.ExternalAiGateway;
import com.studyflow.ai.gateway.LangChain4jAiGateway;
import com.studyflow.ai.gateway.MockAiGateway;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "studyflow.ai.provider", havingValue = "mock", matchIfMissing = true)
    public AiGateway mockAiGateway() {
        return new MockAiGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "studyflow.ai.provider", havingValue = "langchain4j")
    public AiGateway langChain4jAiGateway(ChatLanguageModel chatLanguageModel) {
        return new LangChain4jAiGateway(chatLanguageModel);
    }

    @Bean
    @ConditionalOnProperty(name = "studyflow.ai.provider", havingValue = "external")
    public AiGateway externalAiGateway(AiProperties aiProperties) {
        return new ExternalAiGateway(aiProperties);
    }

    @Bean
    @ConditionalOnMissingBean(AiGateway.class)
    public AiGateway fallbackAiGateway() {
        return new MockAiGateway();
    }
}
