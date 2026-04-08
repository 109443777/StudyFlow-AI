package com.studyflow.ai.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiStudyContentRequest {

    private Long materialId;

    private String prompt;

    private String cleanedText;
}
