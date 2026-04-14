package com.studyflow.ai.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InitUploadDTO {

    @NotBlank(message = "fileName cannot be blank")
    @Size(max = 255, message = "fileName length must be less than 255")
    private String fileName;

    @NotNull(message = "fileSize cannot be null")
    @Min(value = 1, message = "fileSize must be greater than 0")
    private Long fileSize;

    @NotBlank(message = "fileMd5 cannot be blank")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "fileMd5 must be 32 hex characters")
    private String fileMd5;

    @NotBlank(message = "fileSha256 cannot be blank")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "fileSha256 must be 64 hex characters")
    private String fileSha256;
}
