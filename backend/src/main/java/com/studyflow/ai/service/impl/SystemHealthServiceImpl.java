package com.studyflow.ai.service.impl;

import com.studyflow.ai.config.AiProperties;
import com.studyflow.ai.config.EmbeddingProperties;
import com.studyflow.ai.config.LangChainProperties;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.service.SystemHealthService;
import com.studyflow.ai.vo.HealthCheckVO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SystemHealthServiceImpl implements SystemHealthService {

    private final AiProperties aiProperties;

    private final EmbeddingProperties embeddingProperties;

    private final TranscriptionProperties transcriptionProperties;

    private final LangChainProperties langChainProperties;

    @Override
    public HealthCheckVO healthCheck() {
        return HealthCheckVO.builder()
                .application("studyflow-ai-backend")
                .status("UP")
                .aiProvider(aiProperties.getProvider())
                .embeddingProvider(embeddingProperties.getProvider())
                .transcriptionProvider(transcriptionProperties.getProvider())
                .chatModel(displayModel(langChainProperties.getChatModel()))
                .embeddingModel(displayModel(langChainProperties.getEmbeddingModel()))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private String displayModel(String modelName) {
        return StringUtils.hasText(modelName) ? modelName.trim() : "N/A";
    }
}
