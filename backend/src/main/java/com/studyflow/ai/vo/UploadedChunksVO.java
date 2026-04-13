package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadedChunksVO {

    private String uploadId;

    private Integer totalParts;

    private Integer uploadedPartCount;

    private List<UploadedPartVO> uploadedParts;

    private String status;

    private Boolean completed;
}
