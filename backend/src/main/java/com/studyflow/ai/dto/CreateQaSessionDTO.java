package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Create QA session request")
public class CreateQaSessionDTO {

    @Schema(description = "Material id, kept for single-material compatibility")
    private Long materialId;

    @Size(max = 20, message = "materialIds size must be at most 20")
    @Schema(description = "Material ids in this QA session")
    private List<Long> materialIds;

    @Schema(description = "Optional session name")
    private String sessionName;
}
