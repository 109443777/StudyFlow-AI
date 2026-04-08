package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewOutlineContentVO {

    private String summary;

    private List<String> keywords;

    private List<String> keyPoints;

    private List<ChapterReviewFocusVO> chapterFocuses;

    private List<String> reviewChecklist;
}
