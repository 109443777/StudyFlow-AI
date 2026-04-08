package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@Schema(description = "Generate exam study plan request")
public class GenerateStudyPlanDTO {

    @NotNull(message = "materialId cannot be null")
    @Schema(description = "Material id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long materialId;

    @NotNull(message = "examDate cannot be null")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Exam date in yyyy-MM-dd", example = "2026-06-20", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate examDate;

    @Schema(description = "Optional plan name")
    private String planName;
}
