package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_asset")
public class FileAsset extends BaseEntity {

    private Long canonicalMaterialId;

    private String fileSha256;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String objectKey;

    private String materialType;

    private String assetStatus;

    private String parseStatus;
}
