package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Update QA session material scope request")
public class UpdateQaSessionMaterialsDTO {

    @NotEmpty(message = "materialIds cannot be empty")
    @Size(max = 20, message = "materialIds size must be at most 20")
    @Schema(description = "Material ids in this QA session", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> materialIds;
}
