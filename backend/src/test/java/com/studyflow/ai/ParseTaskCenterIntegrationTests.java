package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import com.studyflow.ai.service.ParseTaskService;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ParseTaskCenterIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ParseTaskService parseTaskService;

    @MockBean
    private StorageGateway storageGateway;

    @MockBean
    private ParseTaskMessagePublisher parseTaskMessagePublisher;

    @MockBean
    private MaterialTaskExecutionService materialTaskExecutionService;

    private String token;

    @BeforeEach
    void setUp() {
        reset(storageGateway, parseTaskMessagePublisher, materialTaskExecutionService);

        parseTaskMapper.delete(Wrappers.emptyWrapper());
        uploadSessionMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("parse_task_user");
        user.setPassword("encoded-password");
        user.setNickname("Parse Task User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        doNothing().when(storageGateway).upload(anyString(), any(InputStream.class), anyLong(), anyString());
        when(storageGateway.getFileUrl(anyString()))
                .thenAnswer(invocation -> "http://mock-minio/studyflow/" + invocation.getArgument(0, String.class));
        doNothing().when(parseTaskMessagePublisher).publish(any(), any());
        doNothing().when(materialTaskExecutionService).execute(any(Material.class), any(ParseTaskTypeEnum.class));
    }

    @Test
    void shouldCreateInitialParseTaskAfterMaterialUpload() throws Exception {
        long materialId = uploadMaterial("discrete-math.pdf", "document".getBytes());

        List<ParseTask> tasks = parseTaskMapper.selectList(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, materialId));

        org.junit.jupiter.api.Assertions.assertEquals(1, tasks.size());
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskTypeEnum.TEXT_PARSE.name(), tasks.get(0).getTaskType());
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskStatusEnum.QUEUED.name(), tasks.get(0).getStatus());
        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.TEXT_PARSE));

        mockMvc.perform(get("/api/parse-tasks")
                        .param("materialId", String.valueOf(materialId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].materialId").value(materialId))
                .andExpect(jsonPath("$.data[0].taskType").value("TEXT_PARSE"))
                .andExpect(jsonPath("$.data[0].status").value("QUEUED"));

        mockMvc.perform(get("/api/parse-tasks/{taskId}", tasks.get(0).getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(tasks.get(0).getId()))
                .andExpect(jsonPath("$.data.taskType").value("TEXT_PARSE"));
    }

    @Test
    void shouldCreateFollowUpTasksAndCompleteMaterialParsing() throws Exception {
        long materialId = uploadMaterial("compiler-notes.pdf", "compiler".getBytes());
        ParseTask initialTask = parseTaskMapper.selectOne(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, materialId)
                .eq(ParseTask::getTaskType, ParseTaskTypeEnum.TEXT_PARSE.name())
                .last("limit 1"));

        reset(parseTaskMessagePublisher);
        doNothing().when(parseTaskMessagePublisher).publish(any(), any());

        parseTaskService.processTask(initialTask.getId());

        List<ParseTask> tasks = parseTaskMapper.selectList(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, materialId));
        org.junit.jupiter.api.Assertions.assertEquals(2, tasks.size());
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskStatusEnum.SUCCESS.name(),
                parseTaskMapper.selectById(initialTask.getId()).getStatus());
        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.AI_SUMMARY));

        Material material = materialMapper.selectById(materialId);
        org.junit.jupiter.api.Assertions.assertEquals(MaterialParseStatusEnum.PARSING.name(), material.getParseStatus());

        mockMvc.perform(get("/api/parse-tasks")
                        .param("materialId", String.valueOf(materialId))
                        .param("status", "QUEUED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].taskType").value("AI_SUMMARY"));

        ParseTask aiSummaryTask = tasks.stream()
                .filter(task -> ParseTaskTypeEnum.AI_SUMMARY.name().equals(task.getTaskType()))
                .findFirst()
                .orElseThrow();

        reset(parseTaskMessagePublisher);
        doNothing().when(parseTaskMessagePublisher).publish(any(), any());
        parseTaskService.processTask(aiSummaryTask.getId());
        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.EMBEDDING));

        ParseTask embeddingTask = parseTaskMapper.selectOne(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, materialId)
                .eq(ParseTask::getTaskType, ParseTaskTypeEnum.EMBEDDING.name())
                .last("limit 1"));
        parseTaskService.processTask(embeddingTask.getId());

        List<ParseTask> completedTasks = parseTaskMapper.selectList(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, materialId));
        org.junit.jupiter.api.Assertions.assertTrue(completedTasks.stream()
                .allMatch(task -> ParseTaskStatusEnum.SUCCESS.name().equals(task.getStatus())));
        org.junit.jupiter.api.Assertions.assertEquals(MaterialParseStatusEnum.SUCCESS.name(),
                materialMapper.selectById(materialId).getParseStatus());
    }

    @Test
    void shouldIncreaseRetryCountAndRecordFailureReason() throws Exception {
        long materialId = uploadMaterial("operating-system.pdf", "os".getBytes());
        ParseTask initialTask = parseTaskMapper.selectOne(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, materialId)
                .eq(ParseTask::getTaskType, ParseTaskTypeEnum.TEXT_PARSE.name())
                .last("limit 1"));

        reset(parseTaskMessagePublisher, materialTaskExecutionService);
        doNothing().when(parseTaskMessagePublisher).publish(any(), any());
        doThrow(new RuntimeException("mock parse failure"))
                .when(materialTaskExecutionService)
                .execute(any(Material.class), eq(ParseTaskTypeEnum.TEXT_PARSE));

        parseTaskService.processTask(initialTask.getId());
        parseTaskService.processTask(initialTask.getId());
        parseTaskService.processTask(initialTask.getId());

        ParseTask failedTask = parseTaskMapper.selectById(initialTask.getId());
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskStatusEnum.FAILED.name(), failedTask.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(3, failedTask.getRetryCount());
        org.junit.jupiter.api.Assertions.assertEquals("mock parse failure", failedTask.getFailReason());
        org.junit.jupiter.api.Assertions.assertEquals(MaterialParseStatusEnum.FAILED.name(),
                materialMapper.selectById(materialId).getParseStatus());
        verify(parseTaskMessagePublisher, times(2)).publish(any(), eq(ParseTaskTypeEnum.TEXT_PARSE));
    }

    private long uploadMaterial(String fileName, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "application/octet-stream",
                content);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/materials/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        return uploadJson.path("data").path("id").asLong();
    }
}
