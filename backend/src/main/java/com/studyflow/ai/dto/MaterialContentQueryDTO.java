package com.studyflow.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialContentQueryDTO {

    @NotNull(message = "materialId cannot be null")
    private Long materialId;
}
