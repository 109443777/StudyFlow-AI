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

    @NotNull(message = "chunkIndex cannot be null")
    @Min(value = 0, message = "chunkIndex must be greater than or equal to 0")
    private Integer chunkIndex;

    @NotNull(message = "chunk cannot be null")
    private MultipartFile chunk;
}
