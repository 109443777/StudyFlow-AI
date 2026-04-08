package com.studyflow.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialChunk;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.QaSession;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialContentTypeEnum;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.mapper.MaterialChunkMapper;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.QaMessageMapper;
import com.studyflow.ai.mapper.QaSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.service.ParseTaskService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EmbeddingRagIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialContentMapper materialContentMapper;

    @Autowired
    private MaterialChunkMapper materialChunkMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private QaSessionMapper qaSessionMapper;

    @Autowired
    private QaMessageMapper qaMessageMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ParseTaskService parseTaskService;

    private Long userId;

    private String token;

    @BeforeEach
    void setUp() {
        qaMessageMapper.delete(Wrappers.emptyWrapper());
        qaSessionMapper.delete(Wrappers.emptyWrapper());
        materialChunkMapper.delete(Wrappers.emptyWrapper());
        materialContentMapper.delete(Wrappers.emptyWrapper());
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("embedding_rag_user");
        user.setPassword("encoded-password");
        user.setNickname("Embedding Rag User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);

        userId = user.getId();
        token = jwtTokenProvider.generateToken(userId, user.getUsername());
    }

    @Test
    void shouldBuildEmbeddingIndexAndAnswerQuestionFromMaterial() throws Exception {
        Material material = createMaterial();
        createMaterialContent(material);
        ParseTask embeddingTask = createParseTask(material, ParseTaskTypeEnum.EMBEDDING);

        parseTaskService.processTask(embeddingTask.getId());

        Assertions.assertEquals(ParseTaskStatusEnum.SUCCESS.name(), parseTaskMapper.selectById(embeddingTask.getId()).getStatus());
        Assertions.assertFalse(materialChunkMapper.selectList(Wrappers.<MaterialChunk>lambdaQuery()
                        .eq(MaterialChunk::getMaterialId, material.getId()))
                .isEmpty());
        Assertions.assertTrue(materialChunkMapper.selectList(Wrappers.<MaterialChunk>lambdaQuery()
                        .eq(MaterialChunk::getMaterialId, material.getId()))
                .stream()
                .allMatch(item -> item.getEmbeddingVector() != null && !item.getEmbeddingVector().isBlank()));

        mockMvc.perform(post("/api/qa/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialId": %d,
                                  "sessionName": "Linear Algebra Review"
                                }
                                """.formatted(material.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.sessionName").value("Linear Algebra Review"));

        QaSession qaSession = qaSessionMapper.selectOne(Wrappers.<QaSession>lambdaQuery()
                .eq(QaSession::getMaterialId, material.getId())
                .eq(QaSession::getUserId, userId)
                .last("limit 1"));
        Assertions.assertNotNull(qaSession);

        mockMvc.perform(post("/api/qa/sessions/{sessionId}/ask", qaSession.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is a matrix and why do we use determinants?",
                                  "topK": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(qaSession.getId()))
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.references.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/qa/sessions/{sessionId}/messages", qaSession.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].referenceChunks.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private Material createMaterial() {
        Material material = new Material();
        material.setUserId(userId);
        material.setFileName("linear-algebra-notes.pdf");
        material.setFileType("pdf");
        material.setFileSize(2048L);
        material.setObjectKey("materials/" + userId + "/linear-algebra-notes.pdf");
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
        materialContent.setContentType(MaterialContentTypeEnum.PLAIN_TEXT.name());
        materialContent.setRawText("""
                Matrix algebra studies vectors, matrices, and linear equations.
                A matrix is a rectangular array of numbers used to represent linear transformations and systems of equations.
                Determinants help judge whether a matrix is invertible and whether a linear system has a unique solution.
                Eigenvalues and eigenvectors describe invariant directions of transformation.
                """);
        materialContent.setCleanedText(materialContent.getRawText());
        materialContent.setChapterInfo("[\"Chapter 1 Matrices\",\"Chapter 2 Determinants\"]");
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
