package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitUploadVO {

    private String uploadId;

    private Long materialId;

    private Long fileAssetId;

    private String fileSha256;

    private Long partSize;

    private Integer totalParts;

    private String status;

    private Boolean uploadRequired;

    private Boolean assetReused;
}
