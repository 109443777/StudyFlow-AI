package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Create QA session request")
public class CreateQaSessionDTO {

    @NotNull(message = "materialId cannot be null")
    @Schema(description = "Material id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long materialId;

    @Schema(description = "Optional session name")
    private String sessionName;
}
