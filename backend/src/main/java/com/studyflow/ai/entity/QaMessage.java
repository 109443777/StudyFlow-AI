package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qa_message")
public class QaMessage extends BaseEntity {

    private Long sessionId;

    private Long userId;

    private Long materialId;

    private String role;

    private String content;

    private String referenceChunks;
}
