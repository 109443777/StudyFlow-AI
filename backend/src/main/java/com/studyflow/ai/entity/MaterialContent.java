package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_content")
public class MaterialContent extends BaseEntity {

    private Long materialId;

    private String contentType;

    private String rawText;

    private String cleanedText;

    private String chapterInfo;
}
