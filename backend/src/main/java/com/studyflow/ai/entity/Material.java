package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material")
public class Material extends BaseEntity {

    private Long userId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String objectKey;

    private String materialType;

    private String parseStatus;

    private String uploadStatus;

    private String sourceType;

    @TableField(exist = false)
    private String fileUrl;
}
