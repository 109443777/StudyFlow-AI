package com.studyflow.ai.mq;

public final class MqConstants {

    public static final String TASK_EXCHANGE = "studyflow.task.exchange";
    public static final String MATERIAL_PARSE_QUEUE = "material.parse.queue";
    public static final String AI_SUMMARY_QUEUE = "ai.summary.queue";
    public static final String EMBEDDING_QUEUE = "embedding.queue";
    public static final String DEAD_LETTER_EXCHANGE = "studyflow.task.dlx";
    public static final String DEAD_LETTER_QUEUE = "studyflow.task.dlq";

    public static final String MATERIAL_PARSE_ROUTING_KEY = "material.parse";
    public static final String AI_SUMMARY_ROUTING_KEY = "ai.summary";
    public static final String EMBEDDING_ROUTING_KEY = "embedding";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.letter";

    private MqConstants() {
    }
}
