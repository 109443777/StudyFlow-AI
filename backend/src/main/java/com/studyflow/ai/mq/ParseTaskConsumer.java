package com.studyflow.ai.mq;

import com.studyflow.ai.service.ParseTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseTaskConsumer {

    private final ParseTaskService parseTaskService;

    @RabbitListener(queues = MqConstants.MATERIAL_PARSE_QUEUE)
    public void consumeMaterialParse(ParseTaskMessage message) {
        log.info("Consume material parse message, taskId={}, materialId={}, taskType={}",
                message.getTaskId(), message.getMaterialId(), message.getTaskType());
        parseTaskService.processTask(message.getTaskId());
    }

    @RabbitListener(queues = MqConstants.AI_SUMMARY_QUEUE)
    public void consumeAiSummary(ParseTaskMessage message) {
        log.info("Consume ai summary message, taskId={}, materialId={}, taskType={}",
                message.getTaskId(), message.getMaterialId(), message.getTaskType());
        parseTaskService.processTask(message.getTaskId());
    }

    @RabbitListener(queues = MqConstants.EMBEDDING_QUEUE)
    public void consumeEmbedding(ParseTaskMessage message) {
        log.info("Consume embedding message, taskId={}, materialId={}, taskType={}",
                message.getTaskId(), message.getMaterialId(), message.getTaskType());
        parseTaskService.processTask(message.getTaskId());
    }
}
