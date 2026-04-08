package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialSummaryVO {

    private Long id;

    private Long materialId;

    private String summaryText;

    private List<String> keywords;

    private List<String> keyPoints;

    private List<ChapterHighlightVO> chapterHighlights;

    private List<String> reviewOutline;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
