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
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.UploadSession;
import com.studyflow.ai.entity.User;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialSourceTypeEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MaterialUploadStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.UserStatusEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.UploadSessionMapper;
import com.studyflow.ai.mapper.UserMapper;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import com.studyflow.ai.service.ParseTaskService;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MaterialTextParsingIntegrationTests {

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
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ParseTaskService parseTaskService;

    @Autowired
    private MaterialTaskExecutionService materialTaskExecutionService;

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

        materialContentMapper.delete(Wrappers.emptyWrapper());
        parseTaskMapper.delete(Wrappers.emptyWrapper());
        uploadSessionMapper.delete(Wrappers.emptyWrapper());
        materialMapper.delete(Wrappers.emptyWrapper());
        userMapper.delete(Wrappers.emptyWrapper());

        User user = new User();
        user.setUsername("text_parser_user");
        user.setPassword("encoded-password");
        user.setNickname("Text Parser User");
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
    void shouldParsePdfTaskAndCreateMaterialContent() throws Exception {
        Material material = createMaterial("lecture-notes.pdf", "pdf", MaterialTypeEnum.DOCUMENT, createPdfBytes());
        ParseTask parseTask = createParseTask(material, ParseTaskTypeEnum.TEXT_PARSE);

        parseTaskService.processTask(parseTask.getId());

        MaterialContent materialContent = materialContentMapper.selectOne(Wrappers.<MaterialContent>lambdaQuery()
                .eq(MaterialContent::getMaterialId, material.getId())
                .last("limit 1"));

        org.junit.jupiter.api.Assertions.assertNotNull(materialContent);
        org.junit.jupiter.api.Assertions.assertTrue(materialContent.getRawText().contains("Chapter 1"));
        org.junit.jupiter.api.Assertions.assertTrue(materialContent.getCleanedText().contains("Linear Algebra"));
        org.junit.jupiter.api.Assertions.assertTrue(materialContent.getChapterInfo().contains("Chapter 1"));

        ParseTask updatedTask = parseTaskMapper.selectById(parseTask.getId());
        org.junit.jupiter.api.Assertions.assertEquals(ParseTaskStatusEnum.SUCCESS.name(), updatedTask.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(MaterialParseStatusEnum.PARSING.name(),
                materialMapper.selectById(material.getId()).getParseStatus());
        verify(parseTaskMessagePublisher, times(1)).publish(any(), eq(ParseTaskTypeEnum.AI_SUMMARY));

        mockMvc.perform(get("/api/material-contents")
                        .param("materialId", String.valueOf(material.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.cleanedText").value(org.hamcrest.Matchers.containsString("Linear Algebra")))
                .andExpect(jsonPath("$.data.chapterInfo[0]").value("Chapter 1"));
    }

    @Test
    void shouldSupportTxtMarkdownDocxAndPptxExtraction() throws Exception {
        Material txtMaterial = createMaterial(
                "summary.txt",
                "txt",
                MaterialTypeEnum.TEXT,
                "Chapter 2\nOperating Systems".getBytes(StandardCharsets.UTF_8));
        Material markdownMaterial = createMaterial(
                "outline.md",
                "md",
                MaterialTypeEnum.TEXT,
                "# Section 1\n- Distributed Systems".getBytes(StandardCharsets.UTF_8));
        Material wordMaterial = createMaterial("essay.docx", "docx", MaterialTypeEnum.DOCUMENT, createDocxBytes());
        Material pptMaterial = createMaterial("slides.pptx", "pptx", MaterialTypeEnum.PPT, createPptxBytes());

        materialTaskExecutionService.execute(txtMaterial, ParseTaskTypeEnum.TEXT_PARSE);
        materialTaskExecutionService.execute(markdownMaterial, ParseTaskTypeEnum.TEXT_PARSE);
        materialTaskExecutionService.execute(wordMaterial, ParseTaskTypeEnum.TEXT_PARSE);
        materialTaskExecutionService.execute(pptMaterial, ParseTaskTypeEnum.TEXT_PARSE);

        MaterialContent txtContent = getMaterialContent(txtMaterial.getId());
        MaterialContent markdownContent = getMaterialContent(markdownMaterial.getId());
        MaterialContent wordContent = getMaterialContent(wordMaterial.getId());
        MaterialContent pptContent = getMaterialContent(pptMaterial.getId());

        org.junit.jupiter.api.Assertions.assertTrue(txtContent.getCleanedText().contains("Operating Systems"));
        org.junit.jupiter.api.Assertions.assertTrue(markdownContent.getCleanedText().contains("Distributed Systems"));
        org.junit.jupiter.api.Assertions.assertTrue(wordContent.getCleanedText().contains("Computer Networks"));
        org.junit.jupiter.api.Assertions.assertTrue(pptContent.getCleanedText().contains("Database Systems"));
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

    private MaterialContent getMaterialContent(Long materialId) {
        return materialContentMapper.selectOne(Wrappers.<MaterialContent>lambdaQuery()
                .eq(MaterialContent::getMaterialId, materialId)
                .last("limit 1"));
    }

    private byte[] createPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Chapter 1");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Linear Algebra");
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createDocxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Chapter 3");
            run.addBreak();
            run.setText("Computer Networks");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPptxBytes() throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setAnchor(new Rectangle(50, 50, 400, 100));
            textBox.setText("Section 4");
            textBox.addNewTextParagraph().addNewTextRun().setText("Database Systems");
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
