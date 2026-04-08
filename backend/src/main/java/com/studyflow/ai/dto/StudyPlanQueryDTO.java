package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Study plan query")
public class StudyPlanQueryDTO {

    @Schema(description = "Material id")
    private Long materialId;

    @Schema(description = "Plan type: REVIEW_OUTLINE / EXAM_PLAN")
    private String planType;
}
