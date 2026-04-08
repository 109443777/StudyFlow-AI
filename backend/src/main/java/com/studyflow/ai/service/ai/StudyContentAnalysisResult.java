package com.studyflow.ai.service.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyContentAnalysisResult {

    private String summary;

    private List<String> keywords;

    private List<String> keyPoints;

    private List<ChapterHighlight> chapterHighlights;

    private List<String> reviewOutline;
}
