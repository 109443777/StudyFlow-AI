package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitUploadVO {

    private String uploadId;

    private Long materialId;

    private Integer totalChunks;

    private List<Integer> uploadedChunks;

    private String status;
}
