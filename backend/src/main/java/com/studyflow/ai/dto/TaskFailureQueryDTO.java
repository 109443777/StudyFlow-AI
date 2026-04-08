package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Task failure record query")
public class TaskFailureQueryDTO {

    @Schema(description = "Material id")
    private Long materialId;

    @Schema(description = "Task type")
    private String taskType;

    @Schema(description = "Record status")
    private String recordStatus;
}
