package com.studyflow.ai.config;

import com.studyflow.ai.gateway.ExternalMediaTranscriptionGateway;
import com.studyflow.ai.gateway.MediaTranscriptionGateway;
import com.studyflow.ai.gateway.MockMediaTranscriptionGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaTranscriptionGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "studyflow.transcription.provider", havingValue = "mock", matchIfMissing = true)
    public MediaTranscriptionGateway mockMediaTranscriptionGateway(TranscriptionProperties transcriptionProperties) {
        return new MockMediaTranscriptionGateway(transcriptionProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "studyflow.transcription.provider", havingValue = "external")
    public MediaTranscriptionGateway externalMediaTranscriptionGateway(TranscriptionProperties transcriptionProperties) {
        return new ExternalMediaTranscriptionGateway(transcriptionProperties);
    }

    @Bean
    @ConditionalOnMissingBean(MediaTranscriptionGateway.class)
    public MediaTranscriptionGateway fallbackMediaTranscriptionGateway(TranscriptionProperties transcriptionProperties) {
        return new MockMediaTranscriptionGateway(transcriptionProperties);
    }
}
