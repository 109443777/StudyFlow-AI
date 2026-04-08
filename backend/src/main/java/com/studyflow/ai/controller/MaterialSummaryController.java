package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.ratelimit.RateLimit;
import com.studyflow.ai.common.ratelimit.RateLimitTarget;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.MaterialSummaryQueryDTO;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.service.StudyContentAiService;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.vo.ChapterHighlightVO;
import com.studyflow.ai.vo.MaterialSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Material Summary")
@RestController
@RequestMapping("/api/material-summaries")
@RequiredArgsConstructor
public class MaterialSummaryController {

    private final StudyContentAiService studyContentAiService;

    @LoginRequired
    @Operation(summary = "Get material summary")
    @GetMapping
    public Result<MaterialSummaryVO> getMaterialSummary(@Valid @ModelAttribute MaterialSummaryQueryDTO materialSummaryQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        MaterialSummary materialSummary = studyContentAiService.getByMaterialId(userId, materialSummaryQueryDTO);
        return Result.success(toMaterialSummaryVO(materialSummary));
    }

    @LoginRequired
    @RateLimit(scene = "ai_summary_generate", limit = 5, windowSeconds = 60, target = RateLimitTarget.USER,
            message = "AI summary generation is too frequent, please retry later")
    @Operation(summary = "Generate material summary")
    @PostMapping("/{materialId}/generate")
    public Result<MaterialSummaryVO> generateMaterialSummary(@PathVariable Long materialId) {
        Long userId = UserContext.getRequiredUserId();
        MaterialSummary materialSummary = studyContentAiService.analyzeAndSaveByMaterialId(userId, materialId);
        return Result.success(toMaterialSummaryVO(materialSummary));
    }

    private MaterialSummaryVO toMaterialSummaryVO(MaterialSummary materialSummary) {
        return MaterialSummaryVO.builder()
                .id(materialSummary.getId())
                .materialId(materialSummary.getMaterialId())
                .summaryText(materialSummary.getSummaryText())
                .keywords(studyContentAiService.readStringList(materialSummary.getKeywords()))
                .keyPoints(studyContentAiService.readStringList(materialSummary.getKeyPoints()))
                .chapterHighlights(toChapterHighlightVOs(studyContentAiService.readChapterHighlights(materialSummary.getChapterHighlights())))
                .reviewOutline(studyContentAiService.readStringList(materialSummary.getReviewOutline()))
                .createTime(materialSummary.getCreateTime())
                .updateTime(materialSummary.getUpdateTime())
                .build();
    }

    private List<ChapterHighlightVO> toChapterHighlightVOs(List<ChapterHighlight> chapterHighlights) {
        return chapterHighlights.stream()
                .map(item -> ChapterHighlightVO.builder()
                        .chapterTitle(item.getChapterTitle())
                        .highlights(item.getHighlights())
                        .build())
                .toList();
    }
}
