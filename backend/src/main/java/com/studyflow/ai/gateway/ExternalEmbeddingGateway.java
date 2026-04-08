package com.studyflow.ai.gateway;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.EmbeddingProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import java.util.List;
import org.springframework.util.StringUtils;

public class ExternalEmbeddingGateway implements EmbeddingGateway {

    private final EmbeddingProperties embeddingProperties;

    public ExternalEmbeddingGateway(EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public List<List<Double>> embedDocuments(List<String> texts) {
        validateConfig();
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                "external embedding gateway is reserved for future vector model integration");
    }

    @Override
    public List<Double> embedQuery(String text) {
        validateConfig();
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                "external embedding gateway is reserved for future vector model integration");
    }

    private void validateConfig() {
        if (!StringUtils.hasText(embeddingProperties.getExternal().getBaseUrl())
                || !StringUtils.hasText(embeddingProperties.getExternal().getApiKey())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external embedding gateway is not configured");
        }
    }
}
