package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialVO {

    private Long id;

    private Long userId;

    private Long fileAssetId;

    private Long reuseSourceMaterialId;

    private String fileSha256;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String objectKey;

    private String fileUrl;

    private String materialType;

    private String parseStatus;

    private String uploadStatus;

    private String sourceType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
