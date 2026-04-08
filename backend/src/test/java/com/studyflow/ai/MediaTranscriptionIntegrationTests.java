package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.entity.MediaTranscript;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.TranscriptStatusEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MediaTranscriptMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.ParseTaskService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MediaTranscriptionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private MaterialContentMapper materialContentMapper;

    @Autowired
    private MediaTranscriptMapper mediaTranscriptMapper;

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ParseTaskService parseTaskService;

    @MockBean
    private StorageGateway storageGateway;

    @MockBean
    private ParseTaskMessagePublisher parseTaskMessagePublisher;

    private final Map<String, byte[]> objectStore = new ConcurrentHashMap<>();

    private Long userId;

    private String token;

    @BeforeEach
    void setUp() {
        reset(storageGateway, parseTaskMessagePublisher);
        objectStore.clear();

        mediaTranscriptMapper.delete(Wrappers.emptyWrapper());
        materialContentMapper.delete(Wrappers.emptyWrapper());
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        uploadSessionMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("media_user");
        user.setPassword("encoded-password");
        user.setNickname("Media User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        userId = user.getId();
        token = jwtTokenProvider.generateToken(userId, user.getUsername());

        when(storageGateway.download(any())).thenAnswer(invocation -> {
            String objectKey = invocation.getArgument(0, String.class);
            return new ByteArrayInputStream(objectStore.get(objectKey));
        });
        doNothing().when(parseTaskMessagePublisher).publish(any(), any());
    }

    @Test
    void shouldTranscribeAudioAndPersistUnifiedTextContent() throws Exception {
        Material material = createMaterial(
                "lecture-audio.mp3",
                "mp3",
                MaterialTypeEnum.AUDIO,
                "audio preview for data structures".getBytes(StandardCharsets.UTF_8));
        ParseTask parseTask = createParseTask(material, ParseTaskTypeEnum.AUDIO_TRANSCRIBE);

        parseTaskService.processTask(parseTask.getId());

        MediaTranscript mediaTranscript = mediaTranscriptMapper.selectOne(Wrappers.<MediaTranscript>lambdaQuery()
                .eq(MediaTranscript::getMaterialId, material.getId())
                .last("limit 1"));
        MaterialContent materialContent = materialContentMapper.selectOne(Wrappers.<MaterialContent>lambdaQuery()
                .eq(MaterialContent::getMaterialId, material.getId())
                .last("limit 1"));

        org.junit.jupiter.api.Assertions.assertNotNull(mediaTranscript);
        org.junit.jupiter.api.Assertions.assertEquals(TranscriptStatusEnum.SUCCESS.name(), mediaTranscript.getTranscriptStatus());
        org.junit.jupiter.api.Assertions.assertTrue(mediaTranscript.getTranscriptText().contains("AUDIO material"));
        org.junit.jupiter.api.Assertions.assertNotNull(materialContent);
        org.junit.jupiter.api.Assertions.assertTrue(materialContent.getCleanedText().contains("audio preview for data structures"));

        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.AI_SUMMARY));

        mockMvc.perform(get("/api/media-transcripts")
                        .param("materialId", String.valueOf(material.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.mediaType").value("AUDIO"))
                .andExpect(jsonPath("$.data.transcriptStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transcriptSegments.length()").value(2));

        mockMvc.perform(get("/api/material-contents")
                        .param("materialId", String.valueOf(material.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleanedText").value(org.hamcrest.Matchers.containsString("audio preview for data structures")));
    }

    @Test
    void shouldTranscribeVideoAndCreateFollowUpTasks() {
        Material material = createMaterial(
                "course-video.mp4",
                "mp4",
                MaterialTypeEnum.VIDEO,
                "video preview for operating systems".getBytes(StandardCharsets.UTF_8));
        ParseTask parseTask = createParseTask(material, ParseTaskTypeEnum.VIDEO_TRANSCRIBE);

        parseTaskService.processTask(parseTask.getId());

        List<ParseTask> tasks = parseTaskMapper.selectList(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, material.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(2, tasks.size());
        org.junit.jupiter.api.Assertions.assertTrue(tasks.stream()
                .anyMatch(task -> ParseTaskTypeEnum.AI_SUMMARY.name().equals(task.getTaskType())));
        org.junit.jupiter.api.Assertions.assertFalse(tasks.stream()
                .anyMatch(task -> ParseTaskTypeEnum.EMBEDDING.name().equals(task.getTaskType())));
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskStatusEnum.SUCCESS.name(),
                parseTaskMapper.selectById(parseTask.getId()).getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(MaterialParseStatusEnum.PARSING.name(),
                materialMapper.selectById(material.getId()).getParseStatus());
    }

    private Material createMaterial(String fileName, String fileType, MaterialTypeEnum materialType, byte[] content) {
        Material material = new Material();
        material.setUserId(userId);
        material.setFileName(fileName);
        material.setFileType(fileType);
        material.setFileSize((long) content.length);
        material.setObjectKey("materials/" + userId + "/" + fileName);
        material.setMaterialType(materialType.name());
        material.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
        material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);
        objectStore.put(material.getObjectKey(), content);
        return material;
    }

    private ParseTask createParseTask(Material material, ParseTaskTypeEnum taskType) {
        ParseTask parseTask = new ParseTask();
        parseTask.setMaterialId(material.getId());
        parseTask.setUserId(material.getUserId());
        parseTask.setTaskType(taskType.name());
        parseTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
        parseTask.setRetryCount(0);
        parseTaskMapper.insert(parseTask);
        return parseTask;
    }
}
