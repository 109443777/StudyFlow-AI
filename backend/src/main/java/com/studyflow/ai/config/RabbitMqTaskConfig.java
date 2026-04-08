package com.studyflow.ai.config;

import com.studyflow.ai.mq.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTaskConfig {

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(MqConstants.TASK_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(MqConstants.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue materialParseQueue() {
        return QueueBuilder.durable(MqConstants.MATERIAL_PARSE_QUEUE)
                .deadLetterExchange(MqConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue aiSummaryQueue() {
        return QueueBuilder.durable(MqConstants.AI_SUMMARY_QUEUE)
                .deadLetterExchange(MqConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue embeddingQueue() {
        return QueueBuilder.durable(MqConstants.EMBEDDING_QUEUE)
                .deadLetterExchange(MqConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(MqConstants.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding materialParseBinding() {
        return BindingBuilder.bind(materialParseQueue())
                .to(taskExchange())
                .with(MqConstants.MATERIAL_PARSE_ROUTING_KEY);
    }

    @Bean
    public Binding aiSummaryBinding() {
        return BindingBuilder.bind(aiSummaryQueue())
                .to(taskExchange())
                .with(MqConstants.AI_SUMMARY_ROUTING_KEY);
    }

    @Bean
    public Binding embeddingBinding() {
        return BindingBuilder.bind(embeddingQueue())
                .to(taskExchange())
                .with(MqConstants.EMBEDDING_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(MqConstants.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
