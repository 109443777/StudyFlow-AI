package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qa_session")
public class QaSession extends BaseEntity {

    private Long userId;

    private Long materialId;

    private String sessionName;
}
