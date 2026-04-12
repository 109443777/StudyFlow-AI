package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qa_session_material")
public class QaSessionMaterial extends BaseEntity {

    private Long sessionId;

    private Long userId;

    private Long materialId;
}
