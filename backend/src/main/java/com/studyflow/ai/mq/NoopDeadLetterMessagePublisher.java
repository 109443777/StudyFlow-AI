package com.studyflow.ai.mq;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopDeadLetterMessagePublisher implements DeadLetterMessagePublisher {

    @Override
    public void publish(DeadLetterTaskMessage message) {
        log.warn("Dead-letter publisher unavailable, message retained in failure record, taskId={}, materialId={}",
                message.getTaskId(), message.getMaterialId());
    }
}
