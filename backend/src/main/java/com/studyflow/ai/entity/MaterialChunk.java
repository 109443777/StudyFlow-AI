package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_chunk")
public class MaterialChunk extends BaseEntity {

    private Long materialId;

    private Integer chunkIndex;

    private String chunkText;

    private Integer tokenCount;

    private String embeddingVector;
}
