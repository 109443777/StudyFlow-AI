package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.GenerateReviewOutlineDTO;
import com.studyflow.ai.dto.GenerateStudyPlanDTO;
import com.studyflow.ai.dto.StudyPlanQueryDTO;
import com.studyflow.ai.entity.StudyPlan;
import com.studyflow.ai.enums.StudyPlanTypeEnum;
import com.studyflow.ai.service.ReviewOutlineService;
import com.studyflow.ai.service.StudyPlanService;
import com.studyflow.ai.service.study.DailyStudyPlan;
import com.studyflow.ai.service.study.ExamStudyPlanContent;
import com.studyflow.ai.service.study.ReviewOutlineContent;
import com.studyflow.ai.vo.ChapterReviewFocusVO;
import com.studyflow.ai.vo.DailyStudyPlanVO;
import com.studyflow.ai.vo.ExamStudyPlanContentVO;
import com.studyflow.ai.vo.ReviewOutlineContentVO;
import com.studyflow.ai.vo.StudyPlanDetailVO;
import com.studyflow.ai.vo.StudyPlanHistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Study Support")
@RestController
@RequestMapping("/api/study-plans")
@RequiredArgsConstructor
public class StudyPlanController {

    private final ReviewOutlineService reviewOutlineService;

    private final StudyPlanService studyPlanService;

    @LoginRequired
    @Operation(summary = "Generate review outline")
    @PostMapping("/review-outline")
    public Result<StudyPlanDetailVO> generateReviewOutline(@Valid @RequestBody GenerateReviewOutlineDTO generateReviewOutlineDTO) {
        Long userId = UserContext.getRequiredUserId();
        StudyPlan studyPlan = reviewOutlineService.generate(userId, generateReviewOutlineDTO);
        return Result.success(toDetailVO(studyPlan));
    }

    @LoginRequired
    @Operation(summary = "Generate 7-day exam study plan")
    @PostMapping("/exam-plan")
    public Result<StudyPlanDetailVO> generateExamPlan(@Valid @RequestBody GenerateStudyPlanDTO generateStudyPlanDTO) {
        Long userId = UserContext.getRequiredUserId();
        StudyPlan studyPlan = studyPlanService.generate(userId, generateStudyPlanDTO);
        return Result.success(toDetailVO(studyPlan));
    }

    @LoginRequired
    @Operation(summary = "Get study plan detail")
    @GetMapping("/{planId}")
    public Result<StudyPlanDetailVO> getStudyPlanDetail(@PathVariable Long planId) {
        Long userId = UserContext.getRequiredUserId();
        StudyPlan studyPlan = studyPlanService.getDetail(userId, planId);
        return Result.success(toDetailVO(studyPlan));
    }

    @LoginRequired
    @Operation(summary = "Get my study plans")
    @GetMapping
    public Result<List<StudyPlanHistoryVO>> listStudyPlans(@Valid @ModelAttribute StudyPlanQueryDTO studyPlanQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(studyPlanService.listPlans(userId, studyPlanQueryDTO).stream()
                .map(this::toHistoryVO)
                .toList());
    }

    private StudyPlanHistoryVO toHistoryVO(StudyPlan studyPlan) {
        return StudyPlanHistoryVO.builder()
                .id(studyPlan.getId())
                .materialId(studyPlan.getMaterialId())
                .planType(studyPlan.getPlanType())
                .planName(studyPlan.getPlanName())
                .examDate(studyPlan.getExamDate())
                .status(studyPlan.getStatus())
                .createTime(studyPlan.getCreateTime())
                .updateTime(studyPlan.getUpdateTime())
                .build();
    }

    private StudyPlanDetailVO toDetailVO(StudyPlan studyPlan) {
        ReviewOutlineContentVO reviewOutline = null;
        ExamStudyPlanContentVO examPlan = null;
        if (StudyPlanTypeEnum.REVIEW_OUTLINE.name().equals(studyPlan.getPlanType())) {
            reviewOutline = toReviewOutlineContentVO(reviewOutlineService.readContent(studyPlan.getPlanContent()));
        }
        if (StudyPlanTypeEnum.EXAM_PLAN.name().equals(studyPlan.getPlanType())) {
            examPlan = toExamStudyPlanContentVO(studyPlanService.readContent(studyPlan.getPlanContent()));
        }
        return StudyPlanDetailVO.builder()
                .id(studyPlan.getId())
                .materialId(studyPlan.getMaterialId())
                .planType(studyPlan.getPlanType())
                .planName(studyPlan.getPlanName())
                .examDate(studyPlan.getExamDate())
                .status(studyPlan.getStatus())
                .reviewOutline(reviewOutline)
                .examPlan(examPlan)
                .createTime(studyPlan.getCreateTime())
                .updateTime(studyPlan.getUpdateTime())
                .build();
    }

    private ReviewOutlineContentVO toReviewOutlineContentVO(ReviewOutlineContent content) {
        return ReviewOutlineContentVO.builder()
                .summary(content.getSummary())
                .keywords(content.getKeywords())
                .keyPoints(content.getKeyPoints())
                .chapterFocuses(safeList(content.getChapterFocuses()).stream()
                        .map(item -> ChapterReviewFocusVO.builder()
                                .chapterTitle(item.getChapterTitle())
                                .focusPoints(item.getFocusPoints())
                                .build())
                        .toList())
                .reviewChecklist(content.getReviewChecklist())
                .build();
    }

    private ExamStudyPlanContentVO toExamStudyPlanContentVO(ExamStudyPlanContent content) {
        return ExamStudyPlanContentVO.builder()
                .examDate(content.getExamDate())
                .countdownDays(content.getCountdownDays())
                .dailyPlans(safeList(content.getDailyPlans()).stream().map(this::toDailyStudyPlanVO).toList())
                .finalTips(content.getFinalTips())
                .build();
    }

    private DailyStudyPlanVO toDailyStudyPlanVO(DailyStudyPlan dailyStudyPlan) {
        return DailyStudyPlanVO.builder()
                .dayIndex(dailyStudyPlan.getDayIndex())
                .studyDate(dailyStudyPlan.getStudyDate())
                .theme(dailyStudyPlan.getTheme())
                .focusTopics(dailyStudyPlan.getFocusTopics())
                .tasks(dailyStudyPlan.getTasks())
                .build();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
