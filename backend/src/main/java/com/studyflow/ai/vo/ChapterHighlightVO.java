package com.studyflow.ai.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChapterHighlightVO {

    private String chapterTitle;

    private List<String> highlights;
}
