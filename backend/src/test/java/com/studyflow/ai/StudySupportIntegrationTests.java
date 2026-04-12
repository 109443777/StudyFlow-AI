package com.studyflow.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.entity.StudyPlan;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.StudyPlanTypeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MaterialSummaryMapper;
import com.studyflow.ai.mapper.StudyPlanMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.service.ai.ChapterHighlight;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StudySupportIntegrationTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialSummaryMapper materialSummaryMapper;

    @Autowired
    private StudyPlanMapper studyPlanMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long userId;

    private String token;

    @BeforeEach
    void setUp() {
        studyPlanMapper.delete(Wrappers.emptyWrapper());
        materialSummaryMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("study_support_user");
        user.setPassword("encoded-password");
        user.setNickname("Study Support User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);

        userId = user.getId();
        token = jwtTokenProvider.generateToken(userId, user.getUsername());
    }

    @Test
    void shouldGenerateReviewOutlineAndExamPlan() throws Exception {
        Material material = createMaterial();
        createMaterialSummary(material);

        mockMvc.perform(post("/api/study-plans/review-outline")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialId": %d
                                }
                                """.formatted(material.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planType").value("REVIEW_OUTLINE"))
                .andExpect(jsonPath("$.data.planName").value("database-systems.pdf - 复习提纲"))
                .andExpect(jsonPath("$.data.reviewOutline.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.reviewOutline.reviewChecklist[0]")
                        .value("先串联关系模型、范式、事务、索引之间的逻辑关系。"))
                .andExpect(jsonPath("$.data.reviewOutline.keyPoints.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/study-plans/exam-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialId": %d,
                                  "examDate": "%s"
                                }
                                """.formatted(material.getId(), LocalDate.now().plusDays(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planType").value("EXAM_PLAN"))
                .andExpect(jsonPath("$.data.planName").value("database-systems.pdf - 7天复习计划"))
                .andExpect(jsonPath("$.data.examPlan.dailyPlans.length()").value(7))
                .andExpect(jsonPath("$.data.examPlan.dailyPlans[0].theme").value("搭建知识框架"))
                .andExpect(jsonPath("$.data.examPlan.finalTips.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        StudyPlan examPlan = studyPlanMapper.selectOne(Wrappers.<StudyPlan>lambdaQuery()
                .eq(StudyPlan::getUserId, userId)
                .eq(StudyPlan::getPlanType, StudyPlanTypeEnum.EXAM_PLAN.name())
                .last("limit 1"));

        mockMvc.perform(get("/api/study-plans/{planId}", examPlan.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(examPlan.getId()))
                .andExpect(jsonPath("$.data.examPlan.dailyPlans[0].theme").value("搭建知识框架"));

        mockMvc.perform(get("/api/study-plans")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private Material createMaterial() {
        Material material = new Material();
        material.setUserId(userId);
        material.setFileName("database-systems.pdf");
        material.setFileType("pdf");
        material.setFileSize(1024L);
        material.setObjectKey("materials/" + userId + "/database-systems.pdf");
        material.setMaterialType(MaterialTypeEnum.DOCUMENT.name());
        material.setParseStatus(MaterialParseStatusEnum.SUCCESS.name());
        material.setUploadStatus(MaterialUploadStatusEnum.SUCCESS.name());
        material.setSourceType(MaterialSourceTypeEnum.USER_UPLOAD.name());
        materialMapper.insert(material);
        return material;
    }

    private void createMaterialSummary(Material material) throws Exception {
        MaterialSummary materialSummary = new MaterialSummary();
        materialSummary.setMaterialId(material.getId());
        materialSummary.setSummaryText("This material focuses on relational models, normalization, transactions, and indexing.");
        materialSummary.setKeywords(OBJECT_MAPPER.writeValueAsString(List.of(
                "relational model",
                "normalization",
                "transaction",
                "index")));
        materialSummary.setKeyPoints(OBJECT_MAPPER.writeValueAsString(List.of(
                "Understand the structure of the relational model.",
                "Explain why normalization reduces redundancy.",
                "Master ACID properties and transaction control.",
                "Know when indexes improve query efficiency.")));
        materialSummary.setChapterHighlights(OBJECT_MAPPER.writeValueAsString(List.of(
                ChapterHighlight.builder()
                        .chapterTitle("Relational Model")
                        .highlights(List.of("tables and keys", "constraints"))
                        .build(),
                ChapterHighlight.builder()
                        .chapterTitle("Transactions and Indexes")
                        .highlights(List.of("ACID", "locking", "B+ tree indexes"))
                        .build())));
        materialSummary.setReviewOutline(OBJECT_MAPPER.writeValueAsString(List.of(
                "先串联关系模型、范式、事务、索引之间的逻辑关系。",
                "再按章节复述核心定义和典型应用场景。",
                "最后通过题目验证是否能正确判断设计优劣。")));
        materialSummaryMapper.insert(materialSummary);
    }
}
