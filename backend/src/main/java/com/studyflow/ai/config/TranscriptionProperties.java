package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.transcription")
public class TranscriptionProperties {

    private String provider = "mock";

    private String mockPrefix = "[Mock Transcript]";

    private String ffmpegPath = "ffmpeg";

    private String tempDir = System.getProperty("java.io.tmpdir") + "/studyflow-media";

    private long ffmpegTimeoutSeconds = 600;

    private External external = new External();

    @Data
    public static class External {

        private String baseUrl;

        private String apiKey;

        private String model;

        private long requestTimeoutSeconds = 60;

        private long uploadTimeoutSeconds = 120;

        private long pollIntervalMillis = 1000;

        private int maxPollAttempts = 60;
    }
}
