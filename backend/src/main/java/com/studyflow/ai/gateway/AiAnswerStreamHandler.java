package com.studyflow.ai.gateway;

public interface AiAnswerStreamHandler {

    void onNext(String token);

    default void onComplete() {
    }

    void onError(Throwable throwable);
}
