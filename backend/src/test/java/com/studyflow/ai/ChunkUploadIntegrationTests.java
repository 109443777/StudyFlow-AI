package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ChunkUploadIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StorageGateway storageGateway;

    private final Map<String, byte[]> objectStore = new ConcurrentHashMap<>();

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        objectStore.clear();
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        uploadSessionMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("chunk_user");
        user.setPassword("encoded-password");
        user.setNickname("Chunk User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        doAnswer(invocation -> {
            String objectKey = invocation.getArgument(0, String.class);
            InputStream inputStream = invocation.getArgument(1, InputStream.class);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            inputStream.transferTo(byteArrayOutputStream);
            objectStore.put(objectKey, byteArrayOutputStream.toByteArray());
            return null;
        }).when(storageGateway).upload(anyString(), any(InputStream.class), anyLong(), anyString());

        when(storageGateway.download(anyString())).thenAnswer(invocation -> {
            String objectKey = invocation.getArgument(0, String.class);
            return new ByteArrayInputStream(objectStore.get(objectKey));
        });

        doAnswer(invocation -> {
            String objectKey = invocation.getArgument(0, String.class);
            objectStore.remove(objectKey);
            return null;
        }).when(storageGateway).delete(anyString());

        when(storageGateway.getFileUrl(anyString()))
                .thenAnswer(invocation -> "http://mock-minio/studyflow/" + invocation.getArgument(0, String.class));
    }

    @Test
    void shouldSupportChunkUploadAndResume() throws Exception {
        String initBody = """
                {
                  "fileName": "course-video.mp4",
                  "fileSize": 12,
                  "totalChunks": 3,
                  "fileMd5": "0123456789abcdef0123456789abcdef"
                }
                """;

        MvcResult initResult = mockMvc.perform(post("/api/material-uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalChunks").value(3))
                .andReturn();

        JsonNode initJson = objectMapper.readTree(initResult.getResponse().getContentAsString());
        String uploadId = initJson.path("data").path("uploadId").asText();
        long materialId = initJson.path("data").path("materialId").asLong();

        uploadChunk(uploadId, 0, "AAAA");
        uploadChunk(uploadId, 1, "BBBB");

        mockMvc.perform(get("/api/material-uploads/{uploadId}/chunks", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uploadedChunkCount").value(2))
                .andExpect(jsonPath("$.data.uploadedChunks[0]").value(0))
                .andExpect(jsonPath("$.data.uploadedChunks[1]").value(1));

        mockMvc.perform(multipart("/api/material-uploads/chunk")
                        .file(new MockMultipartFile("chunk", "chunk-0.part", MediaType.APPLICATION_OCTET_STREAM_VALUE, "AAAA".getBytes()))
                        .param("uploadId", uploadId)
                        .param("chunkIndex", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.alreadyUploaded").value(true));

        uploadChunk(uploadId, 2, "CCCC");

        mockMvc.perform(post("/api/material-uploads/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(materialId))
                .andExpect(jsonPath("$.data.fileName").value("course-video.mp4"))
                .andExpect(jsonPath("$.data.parseStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.data.uploadStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.materialType").value("VIDEO"));

        byte[] merged = objectStore.values().stream()
                .filter(bytes -> new String(bytes).equals("AAAABBBBCCCC"))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("AAAABBBBCCCC", new String(merged));

        mockMvc.perform(get("/api/materials/{materialId}", materialId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("UPLOADED"));
    }

    private void uploadChunk(String uploadId, int chunkIndex, String content) throws Exception {
        mockMvc.perform(multipart("/api/material-uploads/chunk")
                        .file(new MockMultipartFile("chunk", "chunk-" + chunkIndex + ".part", MediaType.APPLICATION_OCTET_STREAM_VALUE, content.getBytes()))
                        .param("uploadId", uploadId)
                        .param("chunkIndex", String.valueOf(chunkIndex))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.chunkIndex").value(chunkIndex));
    }
}
