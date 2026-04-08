package com.studyflow.ai.gateway;

import com.studyflow.ai.service.media.MediaTranscriptionResult;

public interface MediaTranscriptionGateway {

    MediaTranscriptionResult transcribe(MediaTranscriptionRequest request);
}
