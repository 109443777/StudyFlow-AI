package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkUploadVO {

    private String uploadId;

    private Integer partNumber;

    private String etag;

    private Integer uploadedPartCount;

    private Boolean alreadyUploaded;

    private Boolean completed;
}
