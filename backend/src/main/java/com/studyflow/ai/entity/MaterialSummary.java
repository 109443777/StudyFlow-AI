package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_summary")
public class MaterialSummary extends BaseEntity {

    private Long materialId;

    private String summaryText;

    private String keywords;

    private String keyPoints;

    private String chapterHighlights;

    private String reviewOutline;
}
