package com.studyflow.ai.service.ai;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudyContentAnalysisResult {

    private String summary;

    private List<String> keywords;

    private List<String> keyPoints;

    private List<ChapterHighlight> chapterHighlights;

    private List<String> reviewOutline;
}
