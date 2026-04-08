package com.studyflow.ai.gateway;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ExternalMediaTranscriptionGateway implements MediaTranscriptionGateway {

    private final TranscriptionProperties transcriptionProperties;

    @Override
    public MediaTranscriptionResult transcribe(MediaTranscriptionRequest request) {
        if (!StringUtils.hasText(transcriptionProperties.getExternal().getBaseUrl())
                || !StringUtils.hasText(transcriptionProperties.getExternal().getApiKey())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "external transcription gateway is not configured");
        }
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                "external transcription gateway is reserved for future vendor integration");
    }
}
