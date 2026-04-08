package com.studyflow.ai.config;

import com.studyflow.ai.mq.DeadLetterMessagePublisher;
import com.studyflow.ai.mq.NoopDeadLetterMessagePublisher;
import com.studyflow.ai.mq.RabbitDeadLetterMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeadLetterPublisherConfig {

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    public DeadLetterMessagePublisher rabbitDeadLetterMessagePublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitDeadLetterMessagePublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(DeadLetterMessagePublisher.class)
    public DeadLetterMessagePublisher noopDeadLetterMessagePublisher() {
        return new NoopDeadLetterMessagePublisher();
    }
}
