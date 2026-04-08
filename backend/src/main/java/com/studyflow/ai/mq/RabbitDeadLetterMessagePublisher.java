package com.studyflow.ai.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@RequiredArgsConstructor
public class RabbitDeadLetterMessagePublisher implements DeadLetterMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(DeadLetterTaskMessage message) {
        rabbitTemplate.convertAndSend(MqConstants.DEAD_LETTER_EXCHANGE, MqConstants.DEAD_LETTER_ROUTING_KEY, message);
        log.warn("Published dead-letter task message, taskId={}, materialId={}, taskType={}, retryCount={}",
                message.getTaskId(), message.getMaterialId(), message.getTaskType(), message.getRetryCount());
    }
}
