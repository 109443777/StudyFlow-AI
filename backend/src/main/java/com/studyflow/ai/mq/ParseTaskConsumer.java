package com.studyflow.ai.mq;

import com.studyflow.ai.service.ParseTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParseTaskConsumer {

    private final ParseTaskService parseTaskService;

    @RabbitListener(queues = MqConstants.MATERIAL_PARSE_QUEUE)
    public void consumeMaterialParse(ParseTaskMessage message) {
        parseTaskService.processTask(message.getTaskId());
    }

    @RabbitListener(queues = MqConstants.AI_SUMMARY_QUEUE)
    public void consumeAiSummary(ParseTaskMessage message) {
        parseTaskService.processTask(message.getTaskId());
    }

    @RabbitListener(queues = MqConstants.EMBEDDING_QUEUE)
    public void consumeEmbedding(ParseTaskMessage message) {
        parseTaskService.processTask(message.getTaskId());
    }
}
