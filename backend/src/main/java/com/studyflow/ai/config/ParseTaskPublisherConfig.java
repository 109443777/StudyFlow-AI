package com.studyflow.ai.config;

import com.studyflow.ai.mq.NoopParseTaskMessagePublisher;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.mq.RabbitParseTaskMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParseTaskPublisherConfig {

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    public ParseTaskMessagePublisher rabbitParseTaskMessagePublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitParseTaskMessagePublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ParseTaskMessagePublisher.class)
    public ParseTaskMessagePublisher noopParseTaskMessagePublisher() {
        return new NoopParseTaskMessagePublisher();
    }
}
