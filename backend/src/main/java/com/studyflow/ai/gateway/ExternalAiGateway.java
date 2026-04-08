package com.studyflow.ai.gateway;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.AiProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import java.util.List;
import org.springframework.util.StringUtils;

public class ExternalAiGateway implements AiGateway {

    private final AiProperties aiProperties;

    public ExternalAiGateway(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public StudyContentAnalysisResult analyzeStudyContent(AiStudyContentRequest request) {
        if (!StringUtils.hasText(aiProperties.getExternal().getBaseUrl())
                || !StringUtils.hasText(aiProperties.getExternal().getApiKey())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external ai gateway is not configured");
        }
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                "external ai gateway is reserved for future model integration");
    }

    @Override
    public String answer(String question, List<String> contexts) {
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                "external ai answer gateway is reserved for future model integration");
    }
}
