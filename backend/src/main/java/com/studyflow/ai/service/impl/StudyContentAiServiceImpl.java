package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.util.ChineseTextSupport;
import com.studyflow.ai.dto.MaterialSummaryQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.AiGateway;
import com.studyflow.ai.gateway.AiStudyContentRequest;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MaterialSummaryMapper;
import com.studyflow.ai.service.StudyContentAiService;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import com.studyflow.ai.service.ai.StudyContentPromptBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyContentAiServiceImpl implements StudyContentAiService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MaterialMapper materialMapper;

    private final MaterialContentMapper materialContentMapper;

    private final MaterialSummaryMapper materialSummaryMapper;

    private final StudyContentPromptBuilder studyContentPromptBuilder;

    private final AiGateway aiGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialSummary analyzeAndSave(Material material) {
        MaterialContent materialContent = materialContentMapper.selectOne(new LambdaQueryWrapper<MaterialContent>()
                .eq(MaterialContent::getMaterialId, material.getId())
                .last("limit 1"));
        if (materialContent == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_CONTENT_NOT_FOUND);
        }
        long startTime = System.currentTimeMillis();
        StudyContentAnalysisResult result = aiGateway.analyzeStudyContent(AiStudyContentRequest.builder()
                .materialId(material.getId())
                .cleanedText(materialContent.getCleanedText())
                .prompt(studyContentPromptBuilder.buildPrompt(material, materialContent))
                .build());
        log.info("AI study content analysis finished, materialId={}, costMs={}",
                material.getId(), System.currentTimeMillis() - startTime);

        MaterialSummary existingSummary = materialSummaryMapper.selectOne(new LambdaQueryWrapper<MaterialSummary>()
                .eq(MaterialSummary::getMaterialId, material.getId())
                .last("limit 1"));
        if (existingSummary == null) {
            MaterialSummary materialSummary = new MaterialSummary();
            materialSummary.setMaterialId(material.getId());
            materialSummary.setSummaryText(result.getSummary());
            materialSummary.setKeywords(writeAsJson(result.getKeywords()));
            materialSummary.setKeyPoints(writeAsJson(result.getKeyPoints()));
            materialSummary.setChapterHighlights(writeAsJson(result.getChapterHighlights()));
            materialSummary.setReviewOutline(writeAsJson(result.getReviewOutline()));
            materialSummaryMapper.insert(materialSummary);
            return materialSummary;
        }
        existingSummary.setSummaryText(result.getSummary());
        existingSummary.setKeywords(writeAsJson(result.getKeywords()));
        existingSummary.setKeyPoints(writeAsJson(result.getKeyPoints()));
        existingSummary.setChapterHighlights(writeAsJson(result.getChapterHighlights()));
        existingSummary.setReviewOutline(writeAsJson(result.getReviewOutline()));
        materialSummaryMapper.updateById(existingSummary);
        return existingSummary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialSummary analyzeAndSaveByMaterialId(Long userId, Long materialId) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialId)
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        if (material.getReuseSourceMaterialId() == null) {
            return analyzeAndSave(material);
        }
        Material sourceMaterial = materialMapper.selectById(material.getReuseSourceMaterialId());
        return analyzeAndSave(sourceMaterial == null ? material : sourceMaterial);
    }

    @Override
    public MaterialSummary getByMaterialId(Long userId, MaterialSummaryQueryDTO materialSummaryQueryDTO) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialSummaryQueryDTO.getMaterialId())
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        Long effectiveMaterialId = material.getReuseSourceMaterialId() == null
                ? material.getId()
                : material.getReuseSourceMaterialId();
        MaterialSummary materialSummary = materialSummaryMapper.selectOne(new LambdaQueryWrapper<MaterialSummary>()
                .eq(MaterialSummary::getMaterialId, effectiveMaterialId)
                .last("limit 1"));
        if (materialSummary == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_SUMMARY_NOT_FOUND);
        }
        if (shouldRefreshToChinese(materialSummary)) {
            log.info("Detected legacy non-Chinese summary, regenerate with Chinese prompts, materialId={}",
                    material.getId());
            Material effectiveMaterial = material.getReuseSourceMaterialId() == null
                    ? material
                    : materialMapper.selectById(material.getReuseSourceMaterialId());
            return analyzeAndSave(effectiveMaterial == null ? material : effectiveMaterial);
        }
        return materialSummary;
    }

    @Override
    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to read string list");
        }
    }

    @Override
    public List<ChapterHighlight> readChapterHighlights(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<ChapterHighlight>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to read chapter highlights");
        }
    }

    private String writeAsJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to write ai analysis result");
        }
    }

    private boolean shouldRefreshToChinese(MaterialSummary materialSummary) {
        if (ChineseTextSupport.containsChinese(materialSummary.getSummaryText())) {
            return false;
        }
        return !ChineseTextSupport.containsChinese(readStringList(materialSummary.getKeywords()))
                || !ChineseTextSupport.containsChinese(readStringList(materialSummary.getKeyPoints()))
                || !ChineseTextSupport.containsChinese(readStringList(materialSummary.getReviewOutline()));
    }
}
