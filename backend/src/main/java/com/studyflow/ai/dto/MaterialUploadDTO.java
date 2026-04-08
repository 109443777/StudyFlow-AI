package com.studyflow.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MaterialUploadDTO {

    @NotNull(message = "file cannot be null")
    private MultipartFile file;
}
