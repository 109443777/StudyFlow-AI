package com.studyflow.ai.mq;

public interface DeadLetterMessagePublisher {

    void publish(DeadLetterTaskMessage message);
}
