package com.studyflow.ai.service.text;

import com.studyflow.ai.common.util.TextCleanupSupport;
import java.util.List;

public abstract class AbstractMaterialTextExtractor implements MaterialTextExtractor {

    protected TextExtractionResult buildResult(String rawText) {
        String safeRawText = rawText == null ? "" : rawText.trim();
        String cleanedText = TextCleanupSupport.cleanText(safeRawText);
        List<String> chapterInfo = TextCleanupSupport.extractChapterInfo(safeRawText);
        return TextExtractionResult.builder()
                .rawText(safeRawText)
                .cleanedText(cleanedText)
                .chapterInfo(chapterInfo)
                .build();
    }
}
