package com.studyflow.ai.mq;

import com.studyflow.ai.enums.ParseTaskTypeEnum;

public interface ParseTaskMessagePublisher {

    void publish(ParseTaskMessage message, ParseTaskTypeEnum taskType);
}
