package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parse_task")
public class ParseTask extends BaseEntity {

    private Long materialId;

    private Long userId;

    private String taskType;

    private String status;

    private Integer retryCount;

    private String failReason;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
