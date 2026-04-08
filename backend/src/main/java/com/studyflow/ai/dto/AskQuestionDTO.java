package com.studyflow.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Ask question request")
public class AskQuestionDTO {

    @NotBlank(message = "question cannot be blank")
    @Schema(description = "Question content", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 10, message = "topK must be at most 10")
    @Schema(description = "Top K retrieved chunks")
    private Integer topK;
}
