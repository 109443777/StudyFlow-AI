package com.studyflow.ai.service.study;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewOutlineContent {

    private String summary;

    private List<String> keywords;

    private List<String> keyPoints;

    private List<ChapterReviewFocus> chapterFocuses;

    private List<String> reviewChecklist;
}
