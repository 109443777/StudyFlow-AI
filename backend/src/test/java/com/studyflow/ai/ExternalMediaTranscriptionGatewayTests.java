package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.MediaTypeEnum;
import com.studyflow.ai.gateway.DashScopeTemporaryFileUploader;
import com.studyflow.ai.gateway.ExternalMediaTranscriptionGateway;
import com.studyflow.ai.gateway.MediaTranscriptionRequest;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
                .fileUrl("http://example.com/audio.wav")
                .build();

        assertEquals("C:/temp/lecture.wav", request.getLocalFilePath());
        assertEquals("http://example.com/audio.wav", request.getFileUrl());
        assertEquals(MediaTypeEnum.AUDIO, request.getMediaType());
        assertNotNull(request);
    }

    @Test
    void shouldSubmitAliyunTaskPollAndParseTranscriptionResult() throws Exception {
        try (FakeAliyunTranscriptionServer server = FakeAliyunTranscriptionServer.start()) {
            TranscriptionProperties properties = testProperties(server);

            MediaTranscriptionResult result = new ExternalMediaTranscriptionGateway(properties)
                    .transcribe(MediaTranscriptionRequest.builder()
                            .materialId(1L)
                            .fileName("lecture.wav")
                            .fileType("wav")
                            .mediaType(MediaTypeEnum.AUDIO)
                            .fileUrl("http://storage.example.com/lecture.wav")
                            .build());

            assertEquals("第一句\n第二句", result.getTranscriptText());
            assertEquals(2200L, result.getDuration());
            assertEquals(2, result.getTranscriptSegments().size());
            assertEquals("第一句", result.getTranscriptSegments().get(0).getText());
            assertEquals(0L, result.getTranscriptSegments().get(0).getStartMillis());
            assertEquals(1000L, result.getTranscriptSegments().get(0).getEndMillis());
            assertEquals("Bearer dashscope-key", server.getAuthorizationHeader());
            assertEquals("enable", server.getAsyncHeader());
            assertEquals(true, server.getSubmitBody().contains("\"file_urls\":[\"http://storage.example.com/lecture.wav\"]"));
        }
    }

    @Test
    void shouldUploadLocalAudioAsDashScopeTemporaryFileAndResolveOssResource() throws Exception {
        Path audioFile = Files.createTempFile("studyflow-audio-", ".wav");
        Files.writeString(audioFile, "fake wav bytes", StandardCharsets.UTF_8);
        try (FakeAliyunTranscriptionServer server = FakeAliyunTranscriptionServer.start()) {
            TranscriptionProperties properties = testProperties(server);

            MediaTranscriptionResult result = new ExternalMediaTranscriptionGateway(properties)
                    .transcribe(MediaTranscriptionRequest.builder()
                            .materialId(1L)
                            .fileName("lecture.wav")
                            .fileType("wav")
                            .mediaType(MediaTypeEnum.AUDIO)
                            .localFilePath(audioFile.toString())
                            .fileUrl("http://localhost:9000/studyflow/lecture.wav")
                            .build());

            assertEquals("第一句\n第二句", result.getTranscriptText());
            assertEquals("enable", server.getOssResourceResolveHeader());
            assertEquals(true, server.getSubmitBody().contains("\"file_urls\":[\"oss://dashscope-instant/studyflow-test/"));
            assertEquals(true, server.getSubmitBody().contains(".wav\"]"));
            assertEquals("POST", server.getUploadMethod());
            assertEquals(true, server.getUploadBody().contains("name=\"file\""));
            assertEquals(true, server.getUploadBody().contains("name=\"Signature\""));
            assertEquals(true, server.getPolicyPath().contains("/api/v1/uploads"));
        } finally {
            Files.deleteIfExists(audioFile);
        }
    }

    @Test
    void shouldRejectMissingFileUrlForExternalTranscription() {
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.getExternal().setBaseUrl("https://dashscope.aliyuncs.com/api/v1");
        properties.getExternal().setApiKey("dashscope-key");
        properties.getExternal().setModel("paraformer-v2");

        assertThrows(BusinessException.class, () -> new ExternalMediaTranscriptionGateway(properties)
                .transcribe(MediaTranscriptionRequest.builder()
                        .mediaType(MediaTypeEnum.AUDIO)
                        .build()));
    }

    @Test
    void shouldFailFastWhenDashScopeTemporaryUploadTimesOut() throws Exception {
        Path audioFile = Files.createTempFile("studyflow-audio-timeout-", ".mp3");
        Files.writeString(audioFile, "fake audio bytes", StandardCharsets.UTF_8);
        try (SlowUploadPolicyServer server = SlowUploadPolicyServer.start()) {
            TranscriptionProperties properties = new TranscriptionProperties();
            properties.getExternal().setBaseUrl(server.baseUrl());
            properties.getExternal().setApiKey("dashscope-key");
            properties.getExternal().setModel("paraformer-v2");
            properties.getExternal().setUploadTimeoutSeconds(1);

            long start = System.nanoTime();
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> new DashScopeTemporaryFileUploader(properties).upload(audioFile));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertTrue(elapsedMillis < 1900L);
            assertTrue(exception.getMessage().contains("timed out"));
        } finally {
            Files.deleteIfExists(audioFile);
        }
    }

    private static TranscriptionProperties testProperties(FakeAliyunTranscriptionServer server) {
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.getExternal().setBaseUrl(server.baseUrl());
        properties.getExternal().setApiKey("dashscope-key");
        properties.getExternal().setModel("paraformer-v2");
        properties.getExternal().setPollIntervalMillis(1);
        properties.getExternal().setMaxPollAttempts(2);
        return properties;
    }

    private static final class FakeAliyunTranscriptionServer implements AutoCloseable {

        private final HttpServer httpServer;

        private volatile String authorizationHeader;

        private volatile String asyncHeader;

        private volatile String ossResourceResolveHeader;

        private volatile String submitBody;

        private volatile String uploadMethod;

        private volatile String uploadBody;

        private volatile String policyPath;

        private FakeAliyunTranscriptionServer(HttpServer httpServer) {
            this.httpServer = httpServer;
        }

        static FakeAliyunTranscriptionServer start() throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            FakeAliyunTranscriptionServer server = new FakeAliyunTranscriptionServer(httpServer);
            httpServer.createContext("/api/v1/services/audio/asr/transcription", server::handleSubmit);
            httpServer.createContext("/api/v1/tasks/task-001", server::handleTask);
            httpServer.createContext("/api/v1/uploads", server::handleUploadPolicy);
            httpServer.createContext("/oss-upload", server::handleUpload);
            httpServer.createContext("/transcriptions/result.json", server::handleResult);
            httpServer.start();
            return server;
        }

        String baseUrl() {
            return "http://localhost:" + httpServer.getAddress().getPort() + "/api/v1";
        }

        String getAuthorizationHeader() {
            return authorizationHeader;
        }

        String getAsyncHeader() {
            return asyncHeader;
        }

        String getOssResourceResolveHeader() {
            return ossResourceResolveHeader;
        }

        String getSubmitBody() {
            return submitBody;
        }

        String getUploadMethod() {
            return uploadMethod;
        }

        String getUploadBody() {
            return uploadBody;
        }

        String getPolicyPath() {
            return policyPath;
        }

        @Override
        public void close() {
            httpServer.stop(0);
        }

        private void handleSubmit(HttpExchange exchange) throws IOException {
            authorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            asyncHeader = exchange.getRequestHeaders().getFirst("X-DashScope-Async");
            ossResourceResolveHeader = exchange.getRequestHeaders().getFirst("X-DashScope-OssResourceResolve");
            submitBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            writeJson(exchange, """
                    {"output":{"task_id":"task-001","task_status":"PENDING"}}
                    """);
        }

        private void handleTask(HttpExchange exchange) throws IOException {
            writeJson(exchange, """
                    {"output":{"task_id":"task-001","task_status":"SUCCEEDED","results":[{"file_url":"http://storage.example.com/lecture.wav","transcription_url":"%s/transcriptions/result.json","subtask_status":"SUCCEEDED"}]}}
                    """.formatted("http://localhost:" + httpServer.getAddress().getPort()));
        }

        private void handleUploadPolicy(HttpExchange exchange) throws IOException {
            policyPath = exchange.getRequestURI().toString();
            writeJson(exchange, """
                    {"data":{"upload_host":"%s/oss-upload","upload_dir":"dashscope-instant/studyflow-test","policy":"test-policy","signature":"test-signature","oss_access_key_id":"test-access-key","x_oss_object_acl":"private","x_oss_forbid_overwrite":"false"}}
                    """.formatted("http://localhost:" + httpServer.getAddress().getPort()));
        }

        private void handleUpload(HttpExchange exchange) throws IOException {
            uploadMethod = exchange.getRequestMethod();
            uploadBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            writeJson(exchange, "{}");
        }

        private void handleResult(HttpExchange exchange) throws IOException {
            writeJson(exchange, """
                    {"transcripts":[{"text":"第一句"},{"text":"第二句"}],"sentences":[{"begin_time":0,"end_time":1000,"text":"第一句"},{"begin_time":1200,"end_time":2200,"text":"第二句"}],"properties":{"original_duration_in_milliseconds":2200}}
                    """);
        }

        private void writeJson(HttpExchange exchange, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static final class SlowUploadPolicyServer implements AutoCloseable {

        private final HttpServer httpServer;

        private SlowUploadPolicyServer(HttpServer httpServer) {
            this.httpServer = httpServer;
        }

        static SlowUploadPolicyServer start() throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            SlowUploadPolicyServer server = new SlowUploadPolicyServer(httpServer);
            httpServer.createContext("/api/v1/uploads", server::handleUploadPolicy);
            httpServer.createContext("/oss-upload", server::handleSlowUpload);
            httpServer.start();
            return server;
        }

        String baseUrl() {
            return "http://localhost:" + httpServer.getAddress().getPort() + "/api/v1";
        }

        @Override
        public void close() {
            httpServer.stop(0);
        }

        private void handleUploadPolicy(HttpExchange exchange) throws IOException {
            byte[] bytes = """
                    {"data":{"upload_host":"%s/oss-upload","upload_dir":"dashscope-instant/studyflow-test","policy":"test-policy","signature":"test-signature","oss_access_key_id":"test-access-key","x_oss_object_acl":"private","x_oss_forbid_overwrite":"false"}}
                    """.formatted("http://localhost:" + httpServer.getAddress().getPort())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private void handleSlowUpload(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(2500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
