package com.studyflow.ai.config;

import com.studyflow.ai.mq.NoopParseTaskMessagePublisher;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.mq.RabbitParseTaskMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParseTaskPublisherConfig {

    @Bean
    public ParseTaskMessagePublisher parseTaskMessagePublisher(ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            return new RabbitParseTaskMessagePublisher(rabbitTemplate);
        }
        return new NoopParseTaskMessagePublisher();
    }
}
