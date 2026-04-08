package com.studyflow.ai.gateway;

import com.studyflow.ai.enums.MediaTypeEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaTranscriptionRequest {

    private Long materialId;

    private String fileName;

    private String fileType;

    private MediaTypeEnum mediaType;

    private byte[] content;
}
