package com.studyflow.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class LangChain4jModelConfig {

    @Bean
    @ConditionalOnProperty(name = "studyflow.ai.provider", havingValue = "langchain4j")
    public ChatLanguageModel studyFlowChatLanguageModel(LangChainProperties langChainProperties) {
        if (!StringUtils.hasText(langChainProperties.getApiKey())) {
            throw new IllegalStateException("studyflow.langchain4j.api-key must be configured when ai provider=langchain4j");
        }
        if (!StringUtils.hasText(langChainProperties.getChatModel())) {
            throw new IllegalStateException("studyflow.langchain4j.chat-model must be configured when ai provider=langchain4j");
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(langChainProperties.getApiKey())
                .modelName(langChainProperties.getChatModel())
                .timeout(langChainProperties.getTimeout())
                .maxRetries(Math.max(0, langChainProperties.getMaxRetries()))
                .logRequests(Boolean.TRUE.equals(langChainProperties.getLogRequests()))
                .logResponses(Boolean.TRUE.equals(langChainProperties.getLogResponses()));
        if (StringUtils.hasText(langChainProperties.getBaseUrl())) {
            builder.baseUrl(langChainProperties.getBaseUrl());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "studyflow.ai.provider", havingValue = "langchain4j")
    public StreamingChatLanguageModel studyFlowStreamingChatLanguageModel(LangChainProperties langChainProperties) {
        if (!StringUtils.hasText(langChainProperties.getApiKey())) {
            throw new IllegalStateException("studyflow.langchain4j.api-key must be configured when ai provider=langchain4j");
        }
        if (!StringUtils.hasText(langChainProperties.getChatModel())) {
            throw new IllegalStateException("studyflow.langchain4j.chat-model must be configured when ai provider=langchain4j");
        }

        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(langChainProperties.getApiKey())
                .modelName(langChainProperties.getChatModel())
                .timeout(langChainProperties.getTimeout())
                .logRequests(Boolean.TRUE.equals(langChainProperties.getLogRequests()))
                .logResponses(Boolean.TRUE.equals(langChainProperties.getLogResponses()));
        if (StringUtils.hasText(langChainProperties.getBaseUrl())) {
            builder.baseUrl(langChainProperties.getBaseUrl());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "studyflow.embedding.provider", havingValue = "langchain4j")
    public EmbeddingModel studyFlowEmbeddingModel(LangChainProperties langChainProperties) {
        if (!StringUtils.hasText(langChainProperties.getApiKey())) {
            throw new IllegalStateException("studyflow.langchain4j.api-key must be configured when embedding provider=langchain4j");
        }
        if (!StringUtils.hasText(langChainProperties.getEmbeddingModel())) {
            throw new IllegalStateException("studyflow.langchain4j.embedding-model must be configured when embedding provider=langchain4j");
        }

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(langChainProperties.getApiKey())
                .modelName(langChainProperties.getEmbeddingModel())
                .timeout(langChainProperties.getTimeout())
                .maxRetries(Math.max(0, langChainProperties.getMaxRetries()))
                .logRequests(Boolean.TRUE.equals(langChainProperties.getLogRequests()))
                .logResponses(Boolean.TRUE.equals(langChainProperties.getLogResponses()));
        if (StringUtils.hasText(langChainProperties.getBaseUrl())) {
            builder.baseUrl(langChainProperties.getBaseUrl());
        }
        return builder.build();
    }
}
