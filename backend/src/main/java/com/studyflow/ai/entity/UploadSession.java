package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("upload_session")
public class UploadSession extends BaseEntity {

    private String uploadId;

    private Long materialId;

    private Long userId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String fileMd5;

    private Integer totalChunks;

    private Integer uploadedChunks;

    private String objectKey;

    private String materialType;

    private String status;

    private String sourceType;
}
