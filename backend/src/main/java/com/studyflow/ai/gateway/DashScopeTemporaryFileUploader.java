package com.studyflow.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class DashScopeTemporaryFileUploader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String OSS_PROTOCOL_PREFIX = "oss://";

    private final TranscriptionProperties transcriptionProperties;

    private final HttpClient httpClient;

    public DashScopeTemporaryFileUploader(TranscriptionProperties transcriptionProperties) {
        this(transcriptionProperties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(transcriptionProperties.getExternal().getRequestTimeoutSeconds()))
                .build());
    }

    DashScopeTemporaryFileUploader(TranscriptionProperties transcriptionProperties, HttpClient httpClient) {
        this.transcriptionProperties = transcriptionProperties;
        this.httpClient = httpClient;
    }

    public String upload(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "local audio file is required for dashscope upload");
        }
        Path multipartFile = null;
        try {
            UploadPolicy uploadPolicy = getUploadPolicy();
            String fileName = safeFileName(filePath);
            String objectKey = uploadPolicy.uploadDir() + "/" + fileName;
            String boundary = "----StudyFlowBoundary" + UUID.randomUUID();
            multipartFile = buildMultipartFile(filePath, uploadPolicy, objectKey, boundary);
            long fileSize = Files.size(filePath);
            log.info("Start uploading temporary audio to DashScope, filePath={}, fileSize={}, objectKey={}",
                    filePath, fileSize, objectKey);
            HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(uploadPolicy.uploadHost()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(transcriptionProperties.getExternal().getUploadTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofFile(multipartFile))
                    .build();
            HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            if (uploadResponse.statusCode() < 200 || uploadResponse.statusCode() >= 300) {
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, buildHttpFailureMessage(
                        "dashscope temporary file upload failed", uploadResponse.statusCode(), uploadResponse.body()));
            }
            log.info("Completed temporary audio upload to DashScope, filePath={}, objectKey={}", filePath, objectKey);
            return OSS_PROTOCOL_PREFIX + objectKey;
        } catch (HttpTimeoutException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "dashscope temporary file upload timed out");
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "dashscope temporary file upload failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "dashscope temporary file upload interrupted");
        } finally {
            if (multipartFile != null) {
                deleteQuietly(multipartFile);
            }
        }
    }

    private UploadPolicy getUploadPolicy() throws IOException, InterruptedException {
        String endpoint = endpoint("/uploads")
                + "?action=getPolicy&model="
                + URLEncoder.encode(transcriptionProperties.getExternal().getModel(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + transcriptionProperties.getExternal().getApiKey())
                .timeout(Duration.ofSeconds(transcriptionProperties.getExternal().getRequestTimeoutSeconds()))
                .GET()
                .build();
        JsonNode data = sendForJson(request).path("data");
        UploadPolicy uploadPolicy = new UploadPolicy(
                data.path("upload_host").asText(""),
                data.path("upload_dir").asText(""),
                data.path("policy").asText(""),
                data.path("signature").asText(""),
                data.path("oss_access_key_id").asText(""),
                data.path("x_oss_object_acl").asText(""),
                data.path("x_oss_forbid_overwrite").asText(""));
        if (!StringUtils.hasText(uploadPolicy.uploadHost())
                || !StringUtils.hasText(uploadPolicy.uploadDir())
                || !StringUtils.hasText(uploadPolicy.policy())
                || !StringUtils.hasText(uploadPolicy.signature())
                || !StringUtils.hasText(uploadPolicy.ossAccessKeyId())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "dashscope temporary upload policy is invalid");
        }
        return uploadPolicy;
    }

    private Path buildMultipartFile(Path sourceFile, UploadPolicy uploadPolicy, String objectKey, String boundary)
            throws IOException {
        Path multipartFile = Files.createTempFile("studyflow-dashscope-upload-", ".multipart");
        try (OutputStream outputStream = Files.newOutputStream(multipartFile)) {
            writeField(outputStream, boundary, "OSSAccessKeyId", uploadPolicy.ossAccessKeyId());
            writeField(outputStream, boundary, "policy", uploadPolicy.policy());
            writeField(outputStream, boundary, "Signature", uploadPolicy.signature());
            writeField(outputStream, boundary, "key", objectKey);
            writeField(outputStream, boundary, "success_action_status", "200");
            writeFieldIfPresent(outputStream, boundary, "x-oss-object-acl", uploadPolicy.xOssObjectAcl());
            writeFieldIfPresent(outputStream, boundary, "x-oss-forbid-overwrite", uploadPolicy.xOssForbidOverwrite());
            writeFile(outputStream, boundary, "file", safeFileName(sourceFile), sourceFile);
            outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return multipartFile;
    }

    private void writeFieldIfPresent(OutputStream outputStream, String boundary, String name, String value)
            throws IOException {
        if (StringUtils.hasText(value)) {
            writeField(outputStream, boundary, name, value);
        }
    }

    private void writeField(OutputStream outputStream, String boundary, String name, String value) throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(OutputStream outputStream, String boundary, String name, String fileName, Path filePath)
            throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Type: " + contentType(fileName) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        Files.copy(filePath, outputStream);
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String contentType(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".wav")) {
            return "audio/wav";
        }
        if (normalized.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (normalized.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    private JsonNode sendForJson(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, buildHttpFailureMessage(
                    "dashscope temporary upload policy request failed", response.statusCode(), response.body()));
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

    private String safeFileName(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "audio-" + UUID.randomUUID() + ".wav" : sanitized;
    }

    private void deleteQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Best effort cleanup for a temporary multipart body.
        }
    }

    private record UploadPolicy(
            String uploadHost,
            String uploadDir,
            String policy,
            String signature,
            String ossAccessKeyId,
            String xOssObjectAcl,
            String xOssForbidOverwrite) {
    }
}
