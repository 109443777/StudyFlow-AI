package com.studyflow.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.studyflow.ai.dto.AbortUploadDTO;
import com.studyflow.ai.entity.FileAsset;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.FileAssetStatusEnum;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.UploadSessionStatusEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.FileAssetMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.service.UploadSessionService;
import com.studyflow.ai.service.upload.UploadProgressCache;
import com.studyflow.ai.vo.UploadedPartVO;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assertions;
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
    private FileAssetMapper fileAssetMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UploadSessionService uploadSessionService;

    @Autowired
    private UploadProgressCache uploadProgressCache;

    @MockBean
    private StorageGateway storageGateway;

    private final Map<String, byte[]> objectStore = new ConcurrentHashMap<>();

    private final Map<String, String> multipartObjectKeys = new ConcurrentHashMap<>();

    private final Map<String, Map<Integer, byte[]>> multipartPartStore = new ConcurrentHashMap<>();

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        objectStore.clear();
        multipartObjectKeys.clear();
        multipartPartStore.clear();
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        uploadSessionMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        fileAssetMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("chunk_user");
        user.setPassword("encoded-password");
        user.setNickname("Chunk User");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        userMapper.insert(user);
        token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        when(storageGateway.initMultipartUpload(anyString(), anyString())).thenAnswer(invocation -> {
            String objectKey = invocation.getArgument(0, String.class);
            String storageUploadId = "storage-" + Math.abs(objectKey.hashCode());
            multipartObjectKeys.put(storageUploadId, objectKey);
            multipartPartStore.put(storageUploadId, new ConcurrentHashMap<>());
            return storageUploadId;
        });

        when(storageGateway.uploadPart(anyString(), anyString(), anyInt(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    String objectKey = invocation.getArgument(0, String.class);
                    String storageUploadId = invocation.getArgument(1, String.class);
                    Integer partNumber = invocation.getArgument(2, Integer.class);
                    InputStream inputStream = invocation.getArgument(3, InputStream.class);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    inputStream.transferTo(byteArrayOutputStream);
                    multipartObjectKeys.put(storageUploadId, objectKey);
                    multipartPartStore.computeIfAbsent(storageUploadId, key -> new ConcurrentHashMap<>())
                            .put(partNumber, byteArrayOutputStream.toByteArray());
                    return "\"etag-%s-%s\"".formatted(storageUploadId, partNumber);
                });

        when(storageGateway.listUploadedParts(anyString(), anyString())).thenAnswer(invocation -> {
            String storageUploadId = invocation.getArgument(1, String.class);
            return multipartPartStore.getOrDefault(storageUploadId, Map.of()).keySet().stream()
                    .sorted()
                    .map(partNumber -> UploadedPartVO.builder()
                            .partNumber(partNumber)
                            .etag("\"etag-%s-%s\"".formatted(storageUploadId, partNumber))
                            .build())
                    .toList();
        });

        doAnswer(invocation -> {
            String objectKey = invocation.getArgument(0, String.class);
            String storageUploadId = invocation.getArgument(1, String.class);
            @SuppressWarnings("unchecked")
            List<UploadedPartVO> uploadedParts = invocation.getArgument(2, List.class);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Map<Integer, byte[]> uploadedPartMap = multipartPartStore.getOrDefault(storageUploadId, Map.of());
            List<UploadedPartVO> sortedParts = new ArrayList<>(uploadedParts);
            sortedParts.sort(Comparator.comparing(UploadedPartVO::getPartNumber));
            for (UploadedPartVO uploadedPart : sortedParts) {
                byteArrayOutputStream.writeBytes(uploadedPartMap.get(uploadedPart.getPartNumber()));
            }
            objectStore.put(objectKey, byteArrayOutputStream.toByteArray());
            multipartPartStore.remove(storageUploadId);
            multipartObjectKeys.remove(storageUploadId);
            return null;
        }).when(storageGateway).completeMultipartUpload(anyString(), anyString(), any());

        doAnswer(invocation -> {
            String storageUploadId = invocation.getArgument(1, String.class);
            multipartPartStore.remove(storageUploadId);
            multipartObjectKeys.remove(storageUploadId);
            return null;
        }).when(storageGateway).abortMultipartUpload(anyString(), anyString());

        when(storageGateway.getFileUrl(anyString()))
                .thenAnswer(invocation -> "http://mock-minio/studyflow/" + invocation.getArgument(0, String.class));
    }

    @Test
    void shouldSupportMultipartUploadAndResume() throws Exception {
        String initBody = """
                {
                  "fileName": "course-video.mp4",
                  "fileSize": 25165824,
                  "fileMd5": "0123456789abcdef0123456789abcdef",
                  "fileSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
                """;

        MvcResult initResult = mockMvc.perform(post("/api/material-uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.partSize").value(8388608))
                .andExpect(jsonPath("$.data.totalParts").value(3))
                .andReturn();

        JsonNode initJson = objectMapper.readTree(initResult.getResponse().getContentAsString());
        String uploadId = initJson.path("data").path("uploadId").asText();
        long materialId = initJson.path("data").path("materialId").asLong();

        uploadPart(uploadId, 1, "AAAA");
        uploadPart(uploadId, 2, "BBBB");

        mockMvc.perform(get("/api/material-uploads/{uploadId}/parts", uploadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uploadedPartCount").value(2))
                .andExpect(jsonPath("$.data.uploadedParts[0].partNumber").value(1))
                .andExpect(jsonPath("$.data.uploadedParts[1].partNumber").value(2));

        mockMvc.perform(multipart("/api/material-uploads/part")
                        .file(new MockMultipartFile("part", "part-1.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, "AAAA".getBytes()))
                        .param("uploadId", uploadId)
                        .param("partNumber", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.alreadyUploaded").value(true));

        uploadPart(uploadId, 3, "CCCC");

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
        Assertions.assertEquals("AAAABBBBCCCC", new String(merged));

        mockMvc.perform(get("/api/materials/{materialId}", materialId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("UPLOADED"));
    }

    @Test
    void shouldAbortMultipartUploadSession() throws Exception {
        String initBody = """
                {
                  "fileName": "cancel-video.mp4",
                  "fileSize": 16777216,
                  "fileMd5": "fedcba9876543210fedcba9876543210",
                  "fileSha256": "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
                }
                """;

        MvcResult initResult = mockMvc.perform(post("/api/material-uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String uploadId = objectMapper.readTree(initResult.getResponse().getContentAsString())
                .path("data")
                .path("uploadId")
                .asText();

        AbortUploadDTO abortUploadDTO = new AbortUploadDTO();
        abortUploadDTO.setUploadId(uploadId);

        mockMvc.perform(post("/api/material-uploads/abort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abortUploadDTO))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRecoverUploadedPartsFromMinioWhenRedisIsMissing() throws Exception {
        String uploadId = initUpload("recovery-video.mp4", 16777216, "11112222333344445555666677778888");

        uploadPart(uploadId, 1, "AAAA");
        uploadPart(uploadId, 2, "BBBB");
        uploadProgressCache.clear(uploadId);

        mockMvc.perform(post("/api/material-uploads/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uploadStatus").value("SUCCESS"));

        verify(storageGateway, times(1)).listUploadedParts(anyString(), anyString());
    }

    @Test
    void shouldAbortExpiredMultipartUploadSessions() throws Exception {
        String uploadId = initUpload("expired-video.mp4", 16777216, "99990000aaaabbbbccccddddeeeeffff");
        var uploadSession = uploadSessionMapper.selectOne(Wrappers.<com.studyflow.ai.entity.UploadSession>lambdaQuery()
                .eq(com.studyflow.ai.entity.UploadSession::getUploadId, uploadId)
                .last("limit 1"));
        uploadSession.setExpireTime(LocalDateTime.now().minusMinutes(5));
        uploadSession.setStatus(UploadSessionStatusEnum.UPLOADING.name());
        uploadSessionMapper.updateById(uploadSession);

        uploadSessionService.abortExpiredUploads();

        var expiredSession = uploadSessionMapper.selectById(uploadSession.getId());
        Assertions.assertEquals(UploadSessionStatusEnum.EXPIRED.name(), expiredSession.getStatus());
        Assertions.assertEquals(MaterialUploadStatusEnum.FAILED.name(),
                materialMapper.selectById(uploadSession.getMaterialId()).getUploadStatus());
        verify(storageGateway, times(1)).abortMultipartUpload(uploadSession.getObjectKey(), uploadSession.getStorageUploadId());
    }

    @Test
    void shouldReuseUploadedFileAssetBySha256AndSkipDuplicateMultipartUpload() throws Exception {
        String fileSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String uploadId = initUpload("shared-video.mp4", 16777216, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", fileSha256);
        uploadPart(uploadId, 1, "AAAA");
        uploadPart(uploadId, 2, "BBBB");
        mockMvc.perform(post("/api/material-uploads/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileSha256").value(fileSha256));

        MvcResult reusedInitResult = mockMvc.perform(post("/api/material-uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "same-video-copy.mp4",
                                  "fileSize": 16777216,
                                  "fileMd5": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                  "fileSha256": "%s"
                                }
                                """.formatted(fileSha256))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadRequired").value(false))
                .andExpect(jsonPath("$.data.assetReused").value(true))
                .andExpect(jsonPath("$.data.fileSha256").value(fileSha256))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn();

        long reusedMaterialId = objectMapper.readTree(reusedInitResult.getResponse().getContentAsString())
                .path("data")
                .path("materialId")
                .asLong();
        Assertions.assertEquals(MaterialUploadStatusEnum.SUCCESS.name(),
                materialMapper.selectById(reusedMaterialId).getUploadStatus());

        List<FileAsset> fileAssets = fileAssetMapper.selectList(Wrappers.<FileAsset>lambdaQuery()
                .eq(FileAsset::getFileSha256, fileSha256));
        Assertions.assertEquals(1, fileAssets.size());
        Assertions.assertEquals(FileAssetStatusEnum.UPLOADED.name(), fileAssets.get(0).getAssetStatus());

        FileAsset parsedAsset = fileAssets.get(0);
        parsedAsset.setParseStatus(MaterialParseStatusEnum.SUCCESS.name());
        fileAssetMapper.updateById(parsedAsset);

        mockMvc.perform(get("/api/materials/{materialId}", reusedMaterialId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value(MaterialParseStatusEnum.SUCCESS.name()));

        verify(storageGateway, times(1)).initMultipartUpload(anyString(), anyString());
    }

    private String initUpload(String fileName, long fileSize, String fileMd5) throws Exception {
        String fileSha256 = fileMd5 + fileMd5;
        return initUpload(fileName, fileSize, fileMd5, fileSha256);
    }

    private String initUpload(String fileName, long fileSize, String fileMd5, String fileSha256) throws Exception {
        String initBody = """
                {
                  "fileName": "%s",
                  "fileSize": %d,
                  "fileMd5": "%s",
                  "fileSha256": "%s"
                }
                """.formatted(fileName, fileSize, fileMd5, fileSha256);
        MvcResult initResult = mockMvc.perform(post("/api/material-uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(initResult.getResponse().getContentAsString())
                .path("data")
                .path("uploadId")
                .asText();
    }

    private void uploadPart(String uploadId, int partNumber, String content) throws Exception {
        mockMvc.perform(multipart("/api/material-uploads/part")
                        .file(new MockMultipartFile("part", "part-" + partNumber + ".bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, content.getBytes()))
                        .param("uploadId", uploadId)
                        .param("partNumber", String.valueOf(partNumber))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.partNumber").value(partNumber));
    }
}
