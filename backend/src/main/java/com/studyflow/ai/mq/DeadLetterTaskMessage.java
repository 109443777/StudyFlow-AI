package com.studyflow.ai.mq;

import com.studyflow.ai.enums.ParseTaskTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterTaskMessage {

    private Long taskId;

    private Long materialId;

    private Long userId;

    private ParseTaskTypeEnum taskType;

    private Integer retryCount;

    private String failReason;
}
