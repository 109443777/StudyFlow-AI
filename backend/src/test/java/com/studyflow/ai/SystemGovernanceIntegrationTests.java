package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.TaskFailureRecord;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialContentTypeEnum;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.TaskFailureRecordMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.mq.DeadLetterMessagePublisher;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import com.studyflow.ai.service.ParseTaskService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SystemGovernanceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialContentMapper materialContentMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private TaskFailureRecordMapper taskFailureRecordMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ParseTaskService parseTaskService;

    @MockBean
    private ParseTaskMessagePublisher parseTaskMessagePublisher;

    @MockBean
    private DeadLetterMessagePublisher deadLetterMessagePublisher;

    @MockBean
    private MaterialTaskExecutionService materialTaskExecutionService;

    private Long userId;

    private String token;

    @BeforeEach
    void setUp() {
        reset(parseTaskMessagePublisher, deadLetterMessagePublisher, materialTaskExecutionService);

        taskFailureRecordMapper.delete(Wrappers.emptyWrapper());
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        materialContentMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("system_governance_user");
        user.setPassword("encoded-password");
        user.setNickname("System Governance User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);

        userId = user.getId();
        token = jwtTokenProvider.generateToken(userId, user.getUsername());

        doNothing().when(parseTaskMessagePublisher).publish(any(), any());
        doNothing().when(deadLetterMessagePublisher).publish(any());
        doNothing().when(materialTaskExecutionService).execute(any(), any());
    }

    @Test
    void shouldKeepInitialTaskDispatchIdempotentAndBlockDuplicateParseRequest() throws Exception {
        Material material = createUploadedMaterial();

        parseTaskService.createAndDispatchInitialTask(material);
        parseTaskService.createAndDispatchInitialTask(materialMapper.selectById(material.getId()));

        Assertions.assertEquals(1L, parseTaskMapper.selectCount(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, material.getId())
                .eq(ParseTask::getTaskType, ParseTaskTypeEnum.TEXT_PARSE.name())));
        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.TEXT_PARSE));

        mockMvc.perform(post("/api/parse-tasks/materials/{materialId}/dispatch", material.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void shouldRecordFailureAndAllowCompensation() throws Exception {
        Material material = createUploadedMaterial();
        ParseTask parseTask = createParseTask(material, ParseTaskTypeEnum.TEXT_PARSE);
        doThrow(new RuntimeException("mock task execution failure"))
                .when(materialTaskExecutionService)
                .execute(any(), eq(ParseTaskTypeEnum.TEXT_PARSE));

        parseTaskService.processTask(parseTask.getId());
        parseTaskService.processTask(parseTask.getId());
        parseTaskService.processTask(parseTask.getId());

        ParseTask failedTask = parseTaskMapper.selectById(parseTask.getId());
        Assertions.assertEquals(ParseTaskStatusEnum.FAILED.name(), failedTask.getStatus());
        Assertions.assertEquals(3, failedTask.getRetryCount());

        TaskFailureRecord record = taskFailureRecordMapper.selectOne(Wrappers.<TaskFailureRecord>lambdaQuery()
                .eq(TaskFailureRecord::getTaskId, parseTask.getId())
                .last("limit 1"));
        Assertions.assertNotNull(record);
        verify(deadLetterMessagePublisher, times(1)).publish(any());

        mockMvc.perform(post("/api/task-failures/{recordId}/compensate", record.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordStatus").value("COMPENSATED"));

        ParseTask compensatedTask = parseTaskMapper.selectById(parseTask.getId());
        Assertions.assertEquals(ParseTaskStatusEnum.QUEUED.name(), compensatedTask.getStatus());
        Assertions.assertEquals(0, compensatedTask.getRetryCount());
    }

    @Test
    void shouldRateLimitAiSummaryGeneration() throws Exception {
        Material material = createUploadedMaterial();
        createMaterialContent(material);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/material-summaries/{materialId}/generate", material.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        mockMvc.perform(post("/api/material-summaries/{materialId}/generate", material.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001));
    }

    private Material createUploadedMaterial() {
        Material material = new Material();
        material.setUserId(userId);
        material.setFileName("governance-test.pdf");
        material.setFileType("pdf");
        material.setFileSize(1024L);
        material.setObjectKey("materials/" + userId + "/governance-test.pdf");
        material.setMaterialType(MaterialTypeEnum.DOCUMENT.name());
        material.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
        material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);
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

    private void createMaterialContent(Material material) {
        MaterialContent materialContent = new MaterialContent();
        materialContent.setMaterialId(material.getId());
        materialContent.setContentType(MaterialContentTypeEnum.PLAIN_TEXT.name());
        materialContent.setRawText("Database systems cover transactions, indexes, normalization, and recovery.");
        materialContent.setCleanedText(materialContent.getRawText());
        materialContent.setChapterInfo("[\"Transactions\",\"Indexes\"]");
        materialContentMapper.insert(materialContent);
    }
}
