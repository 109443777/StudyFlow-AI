package com.studyflow.ai.service.text;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextExtractionResult {

    private String rawText;

    private String cleanedText;

    private List<String> chapterInfo;
}
