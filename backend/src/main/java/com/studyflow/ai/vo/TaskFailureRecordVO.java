package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskFailureRecordVO {

    private Long id;

    private Long taskId;

    private Long materialId;

    private Long userId;

    private String taskType;

    private Integer retryCount;

    private String failReason;

    private String recordStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
