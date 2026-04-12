package com.studyflow.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import com.studyflow.ai.service.media.TranscriptSegment;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class ExternalMediaTranscriptionGateway implements MediaTranscriptionGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TranscriptionProperties transcriptionProperties;

    private final HttpClient httpClient;

    private final DashScopeTemporaryFileUploader temporaryFileUploader;

    public ExternalMediaTranscriptionGateway(TranscriptionProperties transcriptionProperties) {
        this.transcriptionProperties = transcriptionProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(transcriptionProperties.getExternal().getRequestTimeoutSeconds()))
                .build();
        this.temporaryFileUploader = new DashScopeTemporaryFileUploader(transcriptionProperties);
    }

    @Override
    public MediaTranscriptionResult transcribe(MediaTranscriptionRequest request) {
        if (!StringUtils.hasText(transcriptionProperties.getExternal().getBaseUrl())
                || !StringUtils.hasText(transcriptionProperties.getExternal().getApiKey())
                || !StringUtils.hasText(transcriptionProperties.getExternal().getModel())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "external transcription gateway is not configured");
        }
        try {
            String inputUrl = resolveInputUrl(request);
            String taskId = submitTask(inputUrl);
            String transcriptionUrl = waitForTranscriptionUrl(taskId);
            return readTranscriptionResult(transcriptionUrl);
        } catch (HttpTimeoutException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription request timed out");
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription request interrupted");
        }
    }

    private String resolveInputUrl(MediaTranscriptionRequest request) {
        if (shouldUploadTemporaryFile(request)) {
            return temporaryFileUploader.upload(Path.of(request.getLocalFilePath()));
        }
        if (StringUtils.hasText(request.getFileUrl())) {
            return request.getFileUrl();
        }
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "file url is required for external transcription");
    }

    private boolean shouldUploadTemporaryFile(MediaTranscriptionRequest request) {
        if (!StringUtils.hasText(request.getLocalFilePath())) {
            return false;
        }
        Path localFilePath = Path.of(request.getLocalFilePath());
        if (!Files.exists(localFilePath)) {
            return false;
        }
        String fileUrl = request.getFileUrl();
        return !StringUtils.hasText(fileUrl)
                || fileUrl.contains("localhost")
                || fileUrl.contains("127.0.0.1")
                || fileUrl.startsWith("file:");
    }

    private String submitTask(String fileUrl) throws IOException, InterruptedException {
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "model", transcriptionProperties.getExternal().getModel(),
                "input", Map.of("file_urls", List.of(fileUrl))));
        HttpRequest request = requestBuilder(endpoint("/services/audio/asr/transcription"))
                .header("Authorization", "Bearer " + transcriptionProperties.getExternal().getApiKey())
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
                .header("X-DashScope-OssResourceResolve", fileUrl.startsWith("oss://") ? "enable" : "disable")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode output = sendForJson(request).path("output");
        String taskId = output.path("task_id").asText("");
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription task id is empty");
        }
        return taskId;
    }

    private String waitForTranscriptionUrl(String taskId) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < transcriptionProperties.getExternal().getMaxPollAttempts(); attempt++) {
            HttpRequest request = requestBuilder(endpoint("/tasks/" + taskId))
                    .header("Authorization", "Bearer " + transcriptionProperties.getExternal().getApiKey())
                    .GET()
                    .build();
            JsonNode output = sendForJson(request).path("output");
            String status = output.path("task_status").asText("");
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                String transcriptionUrl = findTranscriptionUrl(output);
                if (StringUtils.hasText(transcriptionUrl)) {
                    return transcriptionUrl;
                }
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                        "external transcription result url is empty");
            }
            if ("FAILED".equalsIgnoreCase(status)) {
                String message = output.path("message").asText(output.path("task_metrics").toString());
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                        "external transcription task failed, detail=" + sanitizeBody(message));
            }
            Thread.sleep(transcriptionProperties.getExternal().getPollIntervalMillis());
        }
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription task polling timed out");
    }

    private String findTranscriptionUrl(JsonNode output) {
        JsonNode results = output.path("results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                String transcriptionUrl = result.path("transcription_url").asText("");
                if (StringUtils.hasText(transcriptionUrl)) {
                    return transcriptionUrl;
                }
            }
        }
        return output.path("transcription_url").asText("");
    }

    private MediaTranscriptionResult readTranscriptionResult(String transcriptionUrl)
            throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(transcriptionUrl)
                .GET()
                .build();
        JsonNode root = sendForJson(request);
        String transcriptText = readTranscriptText(root);
        List<TranscriptSegment> segments = readSegments(root);
        long duration = root.path("properties").path("original_duration_in_milliseconds").asLong(0L);
        if (duration <= 0 && !segments.isEmpty()) {
            duration = segments.get(segments.size() - 1).getEndMillis();
        }
        return MediaTranscriptionResult.builder()
                .transcriptText(transcriptText)
                .transcriptSegments(segments)
                .duration(duration)
                .build();
    }

    private String readTranscriptText(JsonNode root) {
        List<String> texts = new ArrayList<>();
        JsonNode transcripts = root.path("transcripts");
        if (transcripts.isArray()) {
            for (JsonNode transcript : transcripts) {
                String text = transcript.path("text").asText("");
                if (StringUtils.hasText(text)) {
                    texts.add(text);
                }
            }
        }
        if (!texts.isEmpty()) {
            return String.join("\n", texts);
        }
        return root.path("text").asText("");
    }

    private List<TranscriptSegment> readSegments(JsonNode root) {
        List<TranscriptSegment> segments = new ArrayList<>();
        JsonNode sentences = root.path("sentences");
        if (sentences.isArray()) {
            int index = 0;
            for (JsonNode sentence : sentences) {
                segments.add(TranscriptSegment.builder()
                        .index(index++)
                        .startMillis(sentence.path("begin_time").asLong())
                        .endMillis(sentence.path("end_time").asLong())
                        .text(sentence.path("text").asText(""))
                        .build());
            }
        }
        return segments;
    }

    private JsonNode sendForJson(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, buildHttpFailureMessage(
                    "external transcription request failed", response.statusCode(), response.body()));
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    private String buildHttpFailureMessage(String message, int statusCode, String body) {
        String sanitizedBody = sanitizeBody(body);
        log.warn("{}, status={}, body={}", message, statusCode, sanitizedBody);
        return message + ", status=" + statusCode + ", body=" + sanitizedBody;
    }

    private String sanitizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String compactBody = body.replaceAll("\\s+", " ").trim();
        if (compactBody.length() > 500) {
            return compactBody.substring(0, 500) + "...";
        }
        return compactBody;
    }

    private String endpoint(String path) {
        String baseUrl = transcriptionProperties.getExternal().getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(transcriptionProperties.getExternal().getRequestTimeoutSeconds()));
    }
}
