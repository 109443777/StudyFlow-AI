package com.studyflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AbortUploadDTO {

    @NotBlank(message = "uploadId cannot be blank")
    private String uploadId;
}
