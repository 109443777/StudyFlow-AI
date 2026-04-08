package com.studyflow.ai.mq;

import com.studyflow.ai.enums.ParseTaskTypeEnum;

public class NoopParseTaskMessagePublisher implements ParseTaskMessagePublisher {

    @Override
    public void publish(ParseTaskMessage message, ParseTaskTypeEnum taskType) {
    }
}
