package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.dto.GenerateStudyPlanDTO;
import com.studyflow.ai.dto.StudyPlanQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.entity.StudyPlan;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.StudyPlanStatusEnum;
import com.studyflow.ai.enums.StudyPlanTypeEnum;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MaterialSummaryMapper;
import com.studyflow.ai.mapper.StudyPlanMapper;
import com.studyflow.ai.service.StudyContentAiService;
import com.studyflow.ai.service.StudyPlanService;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.study.DailyStudyPlan;
import com.studyflow.ai.service.study.ExamStudyPlanContent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StudyPlanServiceImpl implements StudyPlanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final MaterialMapper materialMapper;

    private final MaterialSummaryMapper materialSummaryMapper;

    private final StudyPlanMapper studyPlanMapper;

    private final StudyContentAiService studyContentAiService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyPlan generate(Long userId, GenerateStudyPlanDTO generateStudyPlanDTO) {
        if (generateStudyPlanDTO.getExamDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCodeEnum.EXAM_DATE_INVALID, "exam date cannot be earlier than today");
        }
        Material material = getOwnedMaterial(userId, generateStudyPlanDTO.getMaterialId());
        MaterialSummary materialSummary = getMaterialSummary(material.getId());

        List<String> keyPoints = limitList(studyContentAiService.readStringList(materialSummary.getKeyPoints()), 8);
        List<ChapterHighlight> chapterHighlights = studyContentAiService.readChapterHighlights(materialSummary.getChapterHighlights());
        List<String> reviewChecklist = studyContentAiService.readStringList(materialSummary.getReviewOutline());
        ExamStudyPlanContent examStudyPlanContent = ExamStudyPlanContent.builder()
                .examDate(generateStudyPlanDTO.getExamDate())
                .countdownDays((int) ChronoUnit.DAYS.between(LocalDate.now(), generateStudyPlanDTO.getExamDate()))
                .dailyPlans(buildDailyPlans(generateStudyPlanDTO.getExamDate(), keyPoints, chapterHighlights, reviewChecklist))
                .finalTips(buildFinalTips(reviewChecklist, keyPoints))
                .build();

        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setUserId(userId);
        studyPlan.setMaterialId(material.getId());
        studyPlan.setPlanType(StudyPlanTypeEnum.EXAM_PLAN.name());
        studyPlan.setPlanName(resolvePlanName(material.getFileName(), generateStudyPlanDTO.getPlanName(), "7天复习计划"));
        studyPlan.setExamDate(generateStudyPlanDTO.getExamDate());
        studyPlan.setPlanContent(writeAsJson(examStudyPlanContent));
        studyPlan.setStatus(StudyPlanStatusEnum.ACTIVE.name());
        studyPlanMapper.insert(studyPlan);
        return studyPlan;
    }

    @Override
    public StudyPlan getDetail(Long userId, Long planId) {
        StudyPlan studyPlan = studyPlanMapper.selectOne(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getId, planId)
                .eq(StudyPlan::getUserId, userId)
                .last("limit 1"));
        if (studyPlan == null) {
            throw new BusinessException(ResultCodeEnum.STUDY_PLAN_NOT_FOUND);
        }
        return studyPlan;
    }

    @Override
    public List<StudyPlan> listPlans(Long userId, StudyPlanQueryDTO studyPlanQueryDTO) {
        StudyPlanQueryDTO queryDTO = studyPlanQueryDTO == null ? new StudyPlanQueryDTO() : studyPlanQueryDTO;
        LambdaQueryWrapper<StudyPlan> queryWrapper = new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .orderByDesc(StudyPlan::getCreateTime);
        if (queryDTO.getMaterialId() != null) {
            queryWrapper.eq(StudyPlan::getMaterialId, queryDTO.getMaterialId());
        }
        if (StringUtils.hasText(queryDTO.getPlanType())) {
            queryWrapper.eq(StudyPlan::getPlanType, queryDTO.getPlanType().trim().toUpperCase());
        }
        return studyPlanMapper.selectList(queryWrapper);
    }

    @Override
    public ExamStudyPlanContent readContent(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ExamStudyPlanContent.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to read exam study plan");
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

    private List<DailyStudyPlan> buildDailyPlans(
            LocalDate examDate,
            List<String> keyPoints,
            List<ChapterHighlight> chapterHighlights,
            List<String> reviewChecklist) {
        List<DailyStudyPlan> dailyPlans = new ArrayList<>();
        LocalDate startDate = examDate.minusDays(6);
        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            List<String> focusTopics = resolveFocusTopics(i, keyPoints, chapterHighlights);
            dailyPlans.add(DailyStudyPlan.builder()
                    .dayIndex(i + 1)
                    .studyDate(currentDate)
                    .theme(resolveTheme(i, chapterHighlights))
                    .focusTopics(focusTopics)
                    .tasks(resolveTasks(i, focusTopics, reviewChecklist))
                    .build());
        }
        return dailyPlans;
    }

    private List<String> resolveFocusTopics(int dayIndex, List<String> keyPoints, List<ChapterHighlight> chapterHighlights) {
        if (!chapterHighlights.isEmpty()) {
            ChapterHighlight chapterHighlight = chapterHighlights.get(dayIndex % chapterHighlights.size());
            List<String> focuses = new ArrayList<>();
            focuses.add(chapterHighlight.getChapterTitle());
            focuses.addAll(chapterHighlight.getHighlights().stream().limit(2).toList());
            return focuses;
        }
        int fromIndex = Math.min(dayIndex, Math.max(0, keyPoints.size() - 1));
        return keyPoints.stream().skip(fromIndex).limit(2).toList();
    }

    private String resolveTheme(int dayIndex, List<ChapterHighlight> chapterHighlights) {
        return switch (dayIndex) {
            case 0 -> "搭建知识框架";
            case 1, 2, 3 -> !chapterHighlights.isEmpty()
                    ? "章节重点突破：" + chapterHighlights.get(dayIndex % chapterHighlights.size()).getChapterTitle()
                    : "核心知识点突破";
            case 4 -> "易错点与综合串联";
            case 5 -> "模拟回忆与查漏补缺";
            default -> "考前总复盘";
        };
    }

    private List<String> resolveTasks(int dayIndex, List<String> focusTopics, List<String> reviewChecklist) {
        List<String> tasks = new ArrayList<>();
        if (dayIndex == 0) {
            tasks.add("先阅读摘要和关键词，建立本资料的整体知识框架。");
        }
        for (String topic : focusTopics.stream().limit(3).toList()) {
            tasks.add("围绕“" + topic + "”完成理解、复述和例题回顾。");
        }
        if (!reviewChecklist.isEmpty()) {
            tasks.add(reviewChecklist.get(dayIndex % reviewChecklist.size()));
        }
        if (dayIndex >= 5) {
            tasks.add("进行一次闭卷回忆或口头复述，暴露薄弱点后立即回看原文。");
        }
        return tasks;
    }

    private List<String> buildFinalTips(List<String> reviewChecklist, List<String> keyPoints) {
        List<String> tips = new ArrayList<>();
        tips.add("最后一天优先回看自己仍然说不清的概念，不要平均用力。");
        tips.add("把关键公式、定义、结论进行闭卷回忆，确认能独立写出。");
        if (!keyPoints.isEmpty()) {
            tips.add("考前重点回顾：" + String.join("；", keyPoints.stream().limit(3).toList()));
        }
        if (!reviewChecklist.isEmpty()) {
            tips.add(reviewChecklist.get(reviewChecklist.size() - 1));
        }
        return tips;
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
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to write exam study plan");
        }
    }
}
