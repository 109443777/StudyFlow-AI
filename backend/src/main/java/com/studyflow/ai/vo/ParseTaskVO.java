package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParseTaskVO {

    private Long id;

    private Long materialId;

    private Long userId;

    private String taskType;

    private String status;

    private Integer retryCount;

    private String failReason;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
