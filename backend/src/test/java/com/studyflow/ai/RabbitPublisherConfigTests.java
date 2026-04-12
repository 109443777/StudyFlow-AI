package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyflow.ai.mq.DeadLetterMessagePublisher;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.mq.RabbitDeadLetterMessagePublisher;
import com.studyflow.ai.mq.RabbitParseTaskMessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class RabbitPublisherConfigTests {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ParseTaskMessagePublisher parseTaskMessagePublisher;

    @Autowired
    private DeadLetterMessagePublisher deadLetterMessagePublisher;

    @Test
    void shouldUseRabbitPublishersWhenRabbitTemplateIsAvailable() {
        assertThat(rabbitTemplate).isNotNull();
        assertThat(parseTaskMessagePublisher).isInstanceOf(RabbitParseTaskMessagePublisher.class);
        assertThat(deadLetterMessagePublisher).isInstanceOf(RabbitDeadLetterMessagePublisher.class);
    }
}
