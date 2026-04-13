package com.studyflow.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadChunkDTO {

    @NotBlank(message = "uploadId cannot be blank")
    private String uploadId;

    @NotNull(message = "partNumber cannot be null")
    @Min(value = 1, message = "partNumber must be greater than or equal to 1")
    private Integer partNumber;

    @NotNull(message = "part cannot be null")
    private MultipartFile part;
}
