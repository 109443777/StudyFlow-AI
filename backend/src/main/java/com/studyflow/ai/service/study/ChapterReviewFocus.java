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
public class ChapterReviewFocus {

    private String chapterTitle;

    private List<String> focusPoints;
}
