package com.studyflow.ai.mq;

import com.studyflow.ai.enums.ParseTaskTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RequiredArgsConstructor
public class RabbitParseTaskMessagePublisher implements ParseTaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(ParseTaskMessage message, ParseTaskTypeEnum taskType) {
        rabbitTemplate.convertAndSend(MqConstants.TASK_EXCHANGE, resolveRoutingKey(taskType), message);
    }

    private String resolveRoutingKey(ParseTaskTypeEnum taskType) {
        return switch (taskType) {
            case TEXT_PARSE, AUDIO_TRANSCRIBE, VIDEO_TRANSCRIBE -> MqConstants.MATERIAL_PARSE_ROUTING_KEY;
            case AI_SUMMARY -> MqConstants.AI_SUMMARY_ROUTING_KEY;
            case EMBEDDING -> MqConstants.EMBEDDING_ROUTING_KEY;
        };
    }
}
