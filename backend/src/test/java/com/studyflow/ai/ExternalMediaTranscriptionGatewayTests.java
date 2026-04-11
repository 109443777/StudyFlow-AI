package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.MediaTypeEnum;
import com.studyflow.ai.gateway.MediaTranscriptionRequest;
import org.junit.jupiter.api.Test;

class ExternalMediaTranscriptionGatewayTests {

    @Test
    void shouldExposeFfmpegAndTempDirProperties() {
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath("ffmpeg");
        properties.setTempDir("/tmp/studyflow-media");
        properties.getExternal().setBaseUrl("https://dashscope.aliyuncs.com");
        properties.getExternal().setApiKey("test-key");
        properties.getExternal().setModel("paraformer-v2");

        assertEquals("ffmpeg", properties.getFfmpegPath());
        assertEquals("/tmp/studyflow-media", properties.getTempDir());
        assertEquals("https://dashscope.aliyuncs.com", properties.getExternal().getBaseUrl());
        assertEquals("test-key", properties.getExternal().getApiKey());
        assertEquals("paraformer-v2", properties.getExternal().getModel());
    }

    @Test
    void shouldBuildTranscriptionRequestWithAudioFilePath() {
        MediaTranscriptionRequest request = MediaTranscriptionRequest.builder()
                .materialId(1L)
                .fileName("lecture.wav")
                .fileType("wav")
                .mediaType(MediaTypeEnum.AUDIO)
                .localFilePath("C:/temp/lecture.wav")
                .build();

        assertEquals("C:/temp/lecture.wav", request.getLocalFilePath());
        assertEquals(MediaTypeEnum.AUDIO, request.getMediaType());
        assertNotNull(request);
    }
}
