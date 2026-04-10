# Media Transcription Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 StudyFlow AI 实现“FFmpeg 抽音轨 + 阿里云百炼文件转写”的真实音视频转写链路，并保持与现有任务中心、文本统一回写和后续 AI 摘要流程兼容。

**Architecture:** 在现有 `MediaTranscriptService` 编排层之下新增 `MediaAudioExtractService` 负责视频抽音轨，并将 `ExternalMediaTranscriptionGateway` 改造成真实阿里云百炼文件转写实现。音频与视频在进入 ASR 前统一为音频文件，转写结果继续回写 `media_transcript` 和 `material_content`，任务中心与治理链路保持不变。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MinIO, RabbitMQ, Redis, Jackson, Spring Web, JUnit 5, Mockito

---

## File Map

### Create
- `backend/src/main/java/com/studyflow/ai/service/MediaAudioExtractService.java` - 视频抽音轨服务接口
- `backend/src/main/java/com/studyflow/ai/service/impl/MediaAudioExtractServiceImpl.java` - 基于 FFmpeg 的音轨抽取实现
- `backend/src/main/java/com/studyflow/ai/service/media/AudioExtractionResult.java` - 抽音轨结果对象
- `backend/src/test/java/com/studyflow/ai/MediaAudioExtractServiceTests.java` - 抽音轨服务测试
- `backend/src/test/java/com/studyflow/ai/ExternalMediaTranscriptionGatewayTests.java` - 阿里云转写网关测试

### Modify
- `backend/src/main/java/com/studyflow/ai/config/TranscriptionProperties.java` - 增加 ffmpeg 与阿里云外部配置
- `backend/src/main/java/com/studyflow/ai/gateway/MediaTranscriptionRequest.java` - 改为支持文件路径与原始媒体路径信息
- `backend/src/main/java/com/studyflow/ai/gateway/ExternalMediaTranscriptionGateway.java` - 接入阿里云百炼文件转写 API
- `backend/src/main/java/com/studyflow/ai/service/impl/MediaTranscriptServiceImpl.java` - 区分音频/视频链路并接入抽音轨服务
- `backend/src/main/resources/application.yml` - 增加转写配置项
- `backend/src/test/java/com/studyflow/ai/MediaTranscriptionIntegrationTests.java` - 改造成覆盖视频抽音轨后链路
- `backend/src/test/resources/application.yml` - 如有需要补充测试配置

---

### Task 1: 扩展配置与请求模型

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/config/TranscriptionProperties.java`
- Modify: `backend/src/main/java/com/studyflow/ai/gateway/MediaTranscriptionRequest.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/studyflow/ai/ExternalMediaTranscriptionGatewayTests.java`

- [ ] **Step 1: 写失败测试，定义新配置和请求字段预期**

```java
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
```

- [ ] **Step 2: 运行测试，确认它因为缺少字段而失败**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=ExternalMediaTranscriptionGatewayTests test
```

Expected: FAIL，提示 `setFfmpegPath/getFfmpegPath/getTempDir/localFilePath` 等方法不存在。

- [ ] **Step 3: 最小实现配置字段和请求字段**

```java
// backend/src/main/java/com/studyflow/ai/config/TranscriptionProperties.java
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

    private External external = new External();

    @Data
    public static class External {

        private String baseUrl;

        private String apiKey;

        private String model;
    }
}
```

```java
// backend/src/main/java/com/studyflow/ai/gateway/MediaTranscriptionRequest.java
package com.studyflow.ai.gateway;

import com.studyflow.ai.enums.MediaTypeEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaTranscriptionRequest {

    private Long materialId;

    private String fileName;

    private String fileType;

    private MediaTypeEnum mediaType;

    private byte[] content;

    private String localFilePath;
}
```

```yaml
# backend/src/main/resources/application.yml
studyflow:
  transcription:
    provider: ${TRANSCRIPTION_PROVIDER:mock}
    mock-prefix: ${TRANSCRIPTION_MOCK_PREFIX:[Mock Transcript]}
    ffmpeg-path: ${TRANSCRIPTION_FFMPEG_PATH:ffmpeg}
    temp-dir: ${TRANSCRIPTION_TEMP_DIR:${java.io.tmpdir}/studyflow-media}
    external:
      base-url: ${TRANSCRIPTION_BASE_URL:}
      api-key: ${TRANSCRIPTION_API_KEY:}
      model: ${TRANSCRIPTION_MODEL:paraformer-v2}
```
```

- [ ] **Step 4: 重新运行测试，确认通过**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=ExternalMediaTranscriptionGatewayTests test
```

Expected: PASS

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/studyflow/ai/config/TranscriptionProperties.java backend/src/main/java/com/studyflow/ai/gateway/MediaTranscriptionRequest.java backend/src/main/resources/application.yml backend/src/test/java/com/studyflow/ai/ExternalMediaTranscriptionGatewayTests.java
git commit -m "feat: extend transcription configuration"
```

### Task 2: 实现 FFmpeg 抽音轨服务

**Files:**
- Create: `backend/src/main/java/com/studyflow/ai/service/MediaAudioExtractService.java`
- Create: `backend/src/main/java/com/studyflow/ai/service/impl/MediaAudioExtractServiceImpl.java`
- Create: `backend/src/main/java/com/studyflow/ai/service/media/AudioExtractionResult.java`
- Test: `backend/src/test/java/com/studyflow/ai/MediaAudioExtractServiceTests.java`

- [ ] **Step 1: 写失败测试，先定义抽音轨服务的期望行为**

```java
package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.service.MediaAudioExtractService;
import com.studyflow.ai.service.impl.MediaAudioExtractServiceImpl;
import com.studyflow.ai.service.media.AudioExtractionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaAudioExtractServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractAudioByInvokingConfiguredCommand() throws Exception {
        Path fakeVideo = Files.writeString(tempDir.resolve("input.mp4"), "video-data");
        Path fakeFfmpeg = Files.writeString(tempDir.resolve("fake-ffmpeg.cmd"),
                "@echo off\r\ncopy %3 %9 >nul\r\n");

        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(fakeFfmpeg.toString());
        properties.setTempDir(tempDir.toString());

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);
        AudioExtractionResult result = service.extractToWav(fakeVideo, 1001L);

        assertTrue(Files.exists(result.getAudioFilePath()));
        assertEquals("wav", result.getAudioFilePath().getFileName().toString().substring(result.getAudioFilePath().toString().lastIndexOf('.') + 1));
    }

    @Test
    void shouldThrowBusinessExceptionWhenFfmpegExecutableIsMissing() throws Exception {
        Path fakeVideo = Files.writeString(tempDir.resolve("broken.mp4"), "video-data");
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(tempDir.resolve("missing-ffmpeg.exe").toString());
        properties.setTempDir(tempDir.toString());

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);

        assertThrows(BusinessException.class, () -> service.extractToWav(fakeVideo, 1002L));
    }
}
```

- [ ] **Step 2: 运行测试，确认它因为缺少服务实现而失败**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=MediaAudioExtractServiceTests test
```

Expected: FAIL，提示 `MediaAudioExtractService`, `MediaAudioExtractServiceImpl`, `AudioExtractionResult` 不存在。

- [ ] **Step 3: 最小实现抽音轨服务**

```java
// backend/src/main/java/com/studyflow/ai/service/MediaAudioExtractService.java
package com.studyflow.ai.service;

import com.studyflow.ai.service.media.AudioExtractionResult;
import java.nio.file.Path;

public interface MediaAudioExtractService {

    AudioExtractionResult extractToWav(Path videoPath, Long materialId);
}
```

```java
// backend/src/main/java/com/studyflow/ai/service/media/AudioExtractionResult.java
package com.studyflow.ai.service.media;

import java.nio.file.Path;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioExtractionResult {

    private Path audioFilePath;
}
```

```java
// backend/src/main/java/com/studyflow/ai/service/impl/MediaAudioExtractServiceImpl.java
package com.studyflow.ai.service.impl;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.MediaAudioExtractService;
import com.studyflow.ai.service.media.AudioExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaAudioExtractServiceImpl implements MediaAudioExtractService {

    private final TranscriptionProperties transcriptionProperties;

    @Override
    public AudioExtractionResult extractToWav(Path videoPath, Long materialId) {
        if (!Files.exists(videoPath)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "video file does not exist for audio extraction");
        }
        Path tempDir = ensureTempDir();
        Path audioPath = tempDir.resolve("material-" + materialId + "-extract.wav");
        List<String> command = List.of(
                transcriptionProperties.getFfmpegPath(),
                "-y",
                "-i",
                videoPath.toString(),
                "-vn",
                "-acodec",
                "pcm_s16le",
                audioPath.toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished || process.exitValue() != 0 || !Files.exists(audioPath)) {
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction failed");
            }
            return AudioExtractionResult.builder().audioFilePath(audioPath).build();
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction interrupted");
        }
    }

    private Path ensureTempDir() {
        try {
            Path tempDir = Path.of(transcriptionProperties.getTempDir());
            Files.createDirectories(tempDir);
            return tempDir;
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to create media temp directory");
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=MediaAudioExtractServiceTests test
```

Expected: PASS

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/studyflow/ai/service/MediaAudioExtractService.java backend/src/main/java/com/studyflow/ai/service/impl/MediaAudioExtractServiceImpl.java backend/src/main/java/com/studyflow/ai/service/media/AudioExtractionResult.java backend/src/test/java/com/studyflow/ai/MediaAudioExtractServiceTests.java
git commit -m "feat: add ffmpeg audio extraction service"
```

### Task 3: 接入阿里云百炼文件转写网关

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/gateway/ExternalMediaTranscriptionGateway.java`
- Test: `backend/src/test/java/com/studyflow/ai/ExternalMediaTranscriptionGatewayTests.java`

- [ ] **Step 1: 写失败测试，定义阿里云网关的 HTTP 行为与返回解析**

```java
package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.MediaTypeEnum;
import com.studyflow.ai.gateway.ExternalMediaTranscriptionGateway;
import com.studyflow.ai.gateway.MediaTranscriptionRequest;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalMediaTranscriptionGatewayTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseAliyunTranscriptionResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"output\":{\"text\":\"这是转写结果\",\"segments\":[{\"begin_time\":0,\"end_time\":1200,\"text\":\"第一句\"}]},\"usage\":{}}"));
            server.start();

            Path audio = Files.writeString(tempDir.resolve("sample.wav"), "audio-data");
            TranscriptionProperties properties = new TranscriptionProperties();
            properties.getExternal().setBaseUrl(server.url("/").toString());
            properties.getExternal().setApiKey("dashscope-key");
            properties.getExternal().setModel("paraformer-v2");

            ExternalMediaTranscriptionGateway gateway = new ExternalMediaTranscriptionGateway(properties);
            MediaTranscriptionResult result = gateway.transcribe(MediaTranscriptionRequest.builder()
                    .materialId(1L)
                    .fileName("sample.wav")
                    .fileType("wav")
                    .mediaType(MediaTypeEnum.AUDIO)
                    .localFilePath(audio.toString())
                    .build());

            assertEquals("这是转写结果", result.getTranscriptText());
            assertEquals(1, result.getTranscriptSegments().size());
            assertEquals("第一句", result.getTranscriptSegments().get(0).getText());
        }
    }

    @Test
    void shouldRejectMissingExternalConfiguration() {
        ExternalMediaTranscriptionGateway gateway = new ExternalMediaTranscriptionGateway(new TranscriptionProperties());
        assertThrows(BusinessException.class, () -> gateway.transcribe(MediaTranscriptionRequest.builder().build()));
    }
}
```

- [ ] **Step 2: 运行测试，确认它因为仍是占位实现而失败**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=ExternalMediaTranscriptionGatewayTests test
```

Expected: FAIL，提示未解析响应或抛出 “reserved for future vendor integration”。

- [ ] **Step 3: 最小实现阿里云百炼文件转写网关**

```java
// backend/src/main/java/com/studyflow/ai/gateway/ExternalMediaTranscriptionGateway.java
package com.studyflow.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import com.studyflow.ai.service.media.TranscriptSegment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ExternalMediaTranscriptionGateway implements MediaTranscriptionGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final MediaType BINARY_MEDIA_TYPE = MediaType.parse("application/octet-stream");

    private final TranscriptionProperties transcriptionProperties;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    public MediaTranscriptionResult transcribe(MediaTranscriptionRequest request) {
        if (!StringUtils.hasText(transcriptionProperties.getExternal().getBaseUrl())
                || !StringUtils.hasText(transcriptionProperties.getExternal().getApiKey())
                || !StringUtils.hasText(transcriptionProperties.getExternal().getModel())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription gateway is not configured");
        }
        if (!StringUtils.hasText(request.getLocalFilePath())) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "local audio file path is required for transcription");
        }
        Path audioPath = Path.of(request.getLocalFilePath());
        if (!Files.exists(audioPath)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "audio file does not exist for transcription");
        }
        try {
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", transcriptionProperties.getExternal().getModel())
                    .addFormDataPart("file", audioPath.getFileName().toString(),
                            RequestBody.create(Files.readAllBytes(audioPath), BINARY_MEDIA_TYPE))
                    .build();
            String endpoint = transcriptionProperties.getExternal().getBaseUrl();
            if (!endpoint.endsWith("/")) {
                endpoint = endpoint + "/";
            }
            Request httpRequest = new Request.Builder()
                    .url(endpoint + "api/v1/services/audio/asr/transcription")
                    .header("Authorization", "Bearer " + transcriptionProperties.getExternal().getApiKey())
                    .post(requestBody)
                    .build();
            try (Response response = okHttpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription request failed");
                }
                JsonNode root = OBJECT_MAPPER.readTree(response.body().string());
                JsonNode output = root.path("output");
                String transcriptText = output.path("text").asText("");
                List<TranscriptSegment> segments = new ArrayList<>();
                for (JsonNode segmentNode : output.path("segments")) {
                    segments.add(TranscriptSegment.builder()
                            .startMs(segmentNode.path("begin_time").asLong())
                            .endMs(segmentNode.path("end_time").asLong())
                            .text(segmentNode.path("text").asText(""))
                            .build());
                }
                long duration = segments.isEmpty() ? 0L : segments.get(segments.size() - 1).getEndMs();
                return MediaTranscriptionResult.builder()
                        .transcriptText(transcriptText)
                        .transcriptSegments(segments)
                        .duration(duration)
                        .build();
            }
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "external transcription request failed");
        }
    }
}
```

- [ ] **Step 4: 如测试依赖缺失，补充测试依赖并重跑**

如 `MockWebServer` 不存在，在 `backend/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
```

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=ExternalMediaTranscriptionGatewayTests test
```

Expected: PASS

- [ ] **Step 5: 提交**

```powershell
git add backend/pom.xml backend/src/main/java/com/studyflow/ai/gateway/ExternalMediaTranscriptionGateway.java backend/src/test/java/com/studyflow/ai/ExternalMediaTranscriptionGatewayTests.java
git commit -m "feat: integrate external transcription gateway"
```

### Task 4: 改造媒体转写编排服务，接入视频抽音轨

**Files:**
- Modify: `backend/src/main/java/com/studyflow/ai/service/impl/MediaTranscriptServiceImpl.java`
- Test: `backend/src/test/java/com/studyflow/ai/MediaTranscriptionIntegrationTests.java`

- [ ] **Step 1: 写失败测试，先定义视频链路会调用抽音轨服务再转写**

```java
@Test
void shouldExtractVideoAudioBeforeDelegatingToTranscriptionGateway() {
    // 在现有 MediaTranscriptionIntegrationTests 中新增
    // 预期：VIDEO_TRANSCRIBE 时先从存储下载视频，再调用抽音轨服务，最终回写 media_transcript 和 material_content
}
```

补充完整断言方式：
- 在测试中用 `@MockBean MediaAudioExtractService mediaAudioExtractService`
- 构造一个临时 wav 文件路径返回给 `extractToWav(...)`
- 让 `StorageGateway.download(...)` 返回视频字节
- 断言：
  - `verify(mediaAudioExtractService, times(1)).extractToWav(any(), eq(material.getId()));`
  - `media_transcript.transcriptStatus = SUCCESS`
  - `material_content.cleanedText` 含有返回的模拟转写文本

- [ ] **Step 2: 运行测试，确认它因为服务未接入 `MediaAudioExtractService` 而失败**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=MediaTranscriptionIntegrationTests test
```

Expected: FAIL，提示未调用 `mediaAudioExtractService.extractToWav(...)`。

- [ ] **Step 3: 最小改造 `MediaTranscriptServiceImpl`**

实现要点：
- 注入 `MediaAudioExtractService` 和 `TranscriptionProperties`
- 先将下载流写入临时文件
- 音频任务直接把临时音频文件路径塞进 `MediaTranscriptionRequest.localFilePath`
- 视频任务先调用 `mediaAudioExtractService.extractToWav(videoTempFile, material.getId())`
- 把抽出的 `wav` 文件路径塞进 request
- finally 中尽力删除临时文件，删除失败只打日志

关键代码骨架：

```java
Path originalMediaPath = writeTempFile(material);
Path audioPath = originalMediaPath;
if (mediaType == MediaTypeEnum.VIDEO) {
    audioPath = mediaAudioExtractService.extractToWav(originalMediaPath, material.getId()).getAudioFilePath();
}
MediaTranscriptionResult result = mediaTranscriptionGateway.transcribe(MediaTranscriptionRequest.builder()
        .materialId(material.getId())
        .fileName(audioPath.getFileName().toString())
        .fileType(resolveAudioFileType(audioPath))
        .mediaType(mediaType)
        .localFilePath(audioPath.toString())
        .build());
```

- [ ] **Step 4: 运行集成测试，确认通过**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=MediaTranscriptionIntegrationTests test
```

Expected: PASS

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/studyflow/ai/service/impl/MediaTranscriptServiceImpl.java backend/src/test/java/com/studyflow/ai/MediaTranscriptionIntegrationTests.java
git commit -m "feat: route video transcription through audio extraction"
```

### Task 5: 全链路回归与本地配置说明

**Files:**
- Modify: `backend/README.md`
- Modify: `backend/src/test/resources/application.yml`（如需要）
- Test: `backend/src/test/java/com/studyflow/ai/MediaTranscriptionIntegrationTests.java`

- [ ] **Step 1: 为 README 增加 FFmpeg 与阿里云转写说明**

补充内容：
- 本地需安装 `ffmpeg`
- `application-local.yml` 中如何配置：
  - `studyflow.transcription.provider=external`
  - `studyflow.transcription.ffmpeg-path`
  - `studyflow.transcription.external.base-url`
  - `studyflow.transcription.external.api-key`
  - `studyflow.transcription.external.model`

- [ ] **Step 2: 跑媒体转写相关测试集**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd -Dtest=MediaAudioExtractServiceTests,ExternalMediaTranscriptionGatewayTests,MediaTranscriptionIntegrationTests test
```

Expected: PASS

- [ ] **Step 3: 跑全量测试，确认没有破坏现有模块**

Run:
```powershell
cd F:\光明实验室\studyflow-ai\backend
.\mvnw.cmd test
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```powershell
git add backend/README.md backend/src/test/resources/application.yml
 git commit -m "docs: describe external media transcription setup"
```

## Self-Review
- 规格覆盖：FFmpeg 抽音轨、阿里云转写、编排改造、错误/测试策略均有对应任务；Milvus、Whisper 等非目标未纳入任务。
- 占位词检查：计划正文没有未落地的占位说明或空白实现步骤。
- 类型一致性：新增类型 `MediaAudioExtractService` / `AudioExtractionResult` / `localFilePath` 在任务间保持一致；转写编排与网关配置字段命名统一使用 `ffmpegPath/tempDir/localFilePath`。
