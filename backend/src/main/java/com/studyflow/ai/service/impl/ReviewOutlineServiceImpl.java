package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.dto.GenerateReviewOutlineDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.entity.StudyPlan;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.StudyPlanStatusEnum;
import com.studyflow.ai.enums.StudyPlanTypeEnum;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MaterialSummaryMapper;
import com.studyflow.ai.mapper.StudyPlanMapper;
import com.studyflow.ai.service.ReviewOutlineService;
import com.studyflow.ai.service.StudyContentAiService;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.study.ChapterReviewFocus;
import com.studyflow.ai.service.study.ReviewOutlineContent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReviewOutlineServiceImpl implements ReviewOutlineService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final MaterialMapper materialMapper;

    private final MaterialSummaryMapper materialSummaryMapper;

    private final StudyPlanMapper studyPlanMapper;

    private final StudyContentAiService studyContentAiService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyPlan generate(Long userId, GenerateReviewOutlineDTO generateReviewOutlineDTO) {
        Material material = getOwnedMaterial(userId, generateReviewOutlineDTO.getMaterialId());
        MaterialSummary materialSummary = getMaterialSummary(material.getId());

        List<String> keywords = limitList(studyContentAiService.readStringList(materialSummary.getKeywords()), 8);
        List<String> keyPoints = limitList(studyContentAiService.readStringList(materialSummary.getKeyPoints()), 6);
        List<String> reviewChecklist = resolveReviewChecklist(materialSummary, keyPoints);
        List<ChapterReviewFocus> chapterFocuses = toChapterReviewFocuses(
                studyContentAiService.readChapterHighlights(materialSummary.getChapterHighlights()));

        ReviewOutlineContent reviewOutlineContent = ReviewOutlineContent.builder()
                .summary(materialSummary.getSummaryText())
                .keywords(keywords)
                .keyPoints(keyPoints)
                .chapterFocuses(chapterFocuses)
                .reviewChecklist(reviewChecklist)
                .build();

        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setUserId(userId);
        studyPlan.setMaterialId(material.getId());
        studyPlan.setPlanType(StudyPlanTypeEnum.REVIEW_OUTLINE.name());
        studyPlan.setPlanName(resolvePlanName(material.getFileName(), generateReviewOutlineDTO.getPlanName(), "复习提纲"));
        studyPlan.setPlanContent(writeAsJson(reviewOutlineContent));
        studyPlan.setStatus(StudyPlanStatusEnum.ACTIVE.name());
        studyPlanMapper.insert(studyPlan);
        return studyPlan;
    }

    @Override
    public ReviewOutlineContent readContent(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ReviewOutlineContent.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to read review outline");
        }
    }

    private Material getOwnedMaterial(Long userId, Long materialId) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialId)
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        if (!MaterialParseStatusEnum.SUCCESS.name().equals(material.getParseStatus())
                && !MaterialParseStatusEnum.PARSING.name().equals(material.getParseStatus())) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_SUMMARY_NOT_FOUND, "material is not ready for review outline generation");
        }
        return material;
    }

    private MaterialSummary getMaterialSummary(Long materialId) {
        MaterialSummary materialSummary = materialSummaryMapper.selectOne(new LambdaQueryWrapper<MaterialSummary>()
                .eq(MaterialSummary::getMaterialId, materialId)
                .last("limit 1"));
        if (materialSummary == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_SUMMARY_NOT_FOUND);
        }
        return materialSummary;
    }

    private List<String> resolveReviewChecklist(MaterialSummary materialSummary, List<String> keyPoints) {
        List<String> checklist = studyContentAiService.readStringList(materialSummary.getReviewOutline());
        if (!checklist.isEmpty()) {
            return checklist;
        }
        List<String> fallback = new ArrayList<>();
        fallback.add("先快速通读摘要，建立整份资料的知识框架。");
        for (String keyPoint : keyPoints.stream().limit(4).toList()) {
            fallback.add("重点复述并理解：" + keyPoint);
        }
        fallback.add("用自测题或闭卷回忆检查薄弱点，再回看原文补缺。");
        return fallback;
    }

    private List<ChapterReviewFocus> toChapterReviewFocuses(List<ChapterHighlight> chapterHighlights) {
        if (chapterHighlights.isEmpty()) {
            return List.of();
        }
        return chapterHighlights.stream()
                .map(item -> ChapterReviewFocus.builder()
                        .chapterTitle(item.getChapterTitle())
                        .focusPoints(item.getHighlights())
                        .build())
                .toList();
    }

    private List<String> limitList(List<String> source, int limit) {
        return source == null ? List.of() : source.stream().filter(StringUtils::hasText).limit(limit).toList();
    }

    private String resolvePlanName(String fileName, String requestedName, String suffix) {
        if (StringUtils.hasText(requestedName)) {
            return requestedName.trim();
        }
        return fileName + " - " + suffix;
    }

    private String writeAsJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to write review outline");
        }
    }
}
