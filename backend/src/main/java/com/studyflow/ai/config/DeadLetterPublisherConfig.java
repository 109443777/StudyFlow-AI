package com.studyflow.ai.config;

import com.studyflow.ai.mq.DeadLetterMessagePublisher;
import com.studyflow.ai.mq.NoopDeadLetterMessagePublisher;
import com.studyflow.ai.mq.RabbitDeadLetterMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeadLetterPublisherConfig {

    @Bean
    public DeadLetterMessagePublisher deadLetterMessagePublisher(ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            return new RabbitDeadLetterMessagePublisher(rabbitTemplate);
        }
        return new NoopDeadLetterMessagePublisher();
    }
}
