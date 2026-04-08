package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_failure_record")
public class TaskFailureRecord extends BaseEntity {

    private Long taskId;

    private Long materialId;

    private Long userId;

    private String taskType;

    private Integer retryCount;

    private String failReason;

    private String recordStatus;
}
