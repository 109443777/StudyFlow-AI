package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.auth.JwtTokenProvider;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.UserMapper;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MaterialModuleIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StorageGateway storageGateway;

    private String token;

    @BeforeEach
    void setUp() {
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("material_user");
        user.setPassword("encoded-password");
        user.setNickname("Material User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        doNothing().when(storageGateway).upload(anyString(), any(InputStream.class), anyLong(), anyString());
        when(storageGateway.getFileUrl(anyString()))
                .thenAnswer(invocation -> "http://mock-minio/studyflow/" + invocation.getArgument(0, String.class));
    }

    @Test
    void shouldUploadAndQueryMaterial() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "algebra-notes.pdf",
                "application/pdf",
                "linear algebra".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/materials/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileName").value("algebra-notes.pdf"))
                .andExpect(jsonPath("$.data.materialType").value("DOCUMENT"))
                .andExpect(jsonPath("$.data.parseStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.data.uploadStatus").value("SUCCESS"))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        long materialId = uploadJson.path("data").path("id").asLong();

        mockMvc.perform(get("/api/materials/{materialId}", materialId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(materialId))
                .andExpect(jsonPath("$.data.fileName").value("algebra-notes.pdf"));

        mockMvc.perform(get("/api/materials/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(materialId))
                .andExpect(jsonPath("$.data[0].fileUrl").exists());
    }

    @Test
    void shouldRejectUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "archive.zip",
                "application/zip",
                "zip".getBytes());

        mockMvc.perform(multipart("/api/materials/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003));

        Mockito.verify(storageGateway, Mockito.never())
                .upload(anyString(), any(InputStream.class), anyLong(), anyString());
    }
}
