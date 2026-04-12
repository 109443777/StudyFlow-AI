package com.studyflow.ai.service.rag;

import java.util.List;

public interface QaAnswerStreamObserver {

    void onContext(List<ChunkReference> references);

    void onToken(String token);

    void onComplete(QaAnswerResult result);

    void onError(Throwable throwable);
}
