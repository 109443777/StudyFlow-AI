package com.studyflow.ai.mq;

public final class MqConstants {

    public static final String PARSE_TASK_EXCHANGE = "studyflow.parse.exchange";
    public static final String PARSE_TASK_QUEUE = "studyflow.parse.queue";
    public static final String PARSE_TASK_ROUTING_KEY = "studyflow.parse.route";
    public static final String PARSE_TASK_DLX = "studyflow.parse.dlx";
    public static final String PARSE_TASK_DLQ = "studyflow.parse.dlq";

    private MqConstants() {
    }
}
