package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChapterReviewFocusVO {

    private String chapterTitle;

    private List<String> focusPoints;
}
