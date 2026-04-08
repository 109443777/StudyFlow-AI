package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.common.util.TextCleanupSupport;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MaterialSummaryMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.ParseTaskService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StudyContentAiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialContentMapper materialContentMapper;

    @Autowired
    private MaterialSummaryMapper materialSummaryMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ParseTaskService parseTaskService;

    @MockBean
    private ParseTaskMessagePublisher parseTaskMessagePublisher;

    private Long userId;

    private String token;

    @BeforeEach
    void setUp() {
        reset(parseTaskMessagePublisher);

        materialSummaryMapper.delete(Wrappers.emptyWrapper());
        materialContentMapper.delete(Wrappers.emptyWrapper());
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        uploadSessionMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("study_content_ai_user");
        user.setPassword("encoded-password");
        user.setNickname("Study Content AI User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        userId = user.getId();
        token = jwtTokenProvider.generateToken(userId, user.getUsername());

        doNothing().when(parseTaskMessagePublisher).publish(any(), any());
    }

    @Test
    void shouldAnalyzeStudyContentAndCreateEmbeddingTask() throws Exception {
        Material material = createMaterial();
        createMaterialContent(material);
        ParseTask aiSummaryTask = createParseTask(material, ParseTaskTypeEnum.AI_SUMMARY);

        parseTaskService.processTask(aiSummaryTask.getId());

        MaterialSummary materialSummary = materialSummaryMapper.selectOne(Wrappers.<MaterialSummary>lambdaQuery()
                .eq(MaterialSummary::getMaterialId, material.getId())
                .last("limit 1"));
        org.junit.jupiter.api.Assertions.assertNotNull(materialSummary);
        org.junit.jupiter.api.Assertions.assertFalse(materialSummary.getSummaryText().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(materialSummary.getKeywords().contains("线性代数")
                || materialSummary.getKeywords().contains("矩阵"));

        ParseTask embeddingTask = parseTaskMapper.selectOne(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getMaterialId, material.getId())
                .eq(ParseTask::getTaskType, ParseTaskTypeEnum.EMBEDDING.name())
                .last("limit 1"));
        org.junit.jupiter.api.Assertions.assertNotNull(embeddingTask);
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskStatusEnum.QUEUED.name(), embeddingTask.getStatus());
        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.EMBEDDING));

        mockMvc.perform(get("/api/material-summaries")
                        .param("materialId", String.valueOf(material.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.summaryText").isNotEmpty())
                .andExpect(jsonPath("$.data.keywords.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.keyPoints.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.reviewOutline.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private Material createMaterial() {
        Material material = new Material();
        material.setUserId(userId);
        material.setFileName("linear-algebra.pdf");
        material.setFileType("pdf");
        material.setFileSize(1024L);
        material.setObjectKey("materials/" + userId + "/linear-algebra.pdf");
        material.setMaterialType(MaterialTypeEnum.DOCUMENT.name());
        material.setParseStatus(MaterialParseStatusEnum.PARSING.name());
        material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);
        return material;
    }

    private void createMaterialContent(Material material) {
        MaterialContent materialContent = new MaterialContent();
        materialContent.setMaterialId(material.getId());
        materialContent.setContentType("PLAIN_TEXT");
        materialContent.setRawText("Chapter 1 矩阵与线性方程组。矩阵运算、秩、行列式。Chapter 2 向量空间与线性变换。");
        materialContent.setCleanedText("Chapter 1 矩阵与线性方程组。矩阵运算、秩、行列式。Chapter 2 向量空间与线性变换。");
        materialContent.setChapterInfo(TextCleanupSupport.writeChapterInfo(List.of("Chapter 1 矩阵与线性方程组", "Chapter 2 向量空间与线性变换")));
        materialContentMapper.insert(materialContent);
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
