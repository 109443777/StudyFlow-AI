package com.studyflow.ai.service.ai;

import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.enums.MaterialTypeEnum;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudyContentPromptBuilder {

    public String buildPrompt(Material material, MaterialContent materialContent) {
        String learningScene = resolveLearningScene(material.getMaterialType());
        List<String> chapterInfo = com.studyflow.ai.common.util.TextCleanupSupport.readChapterInfo(materialContent.getChapterInfo());
        return """
                You are StudyFlow AI, a learning assistant for university students.
                Your job is not to write enterprise reports. Your job is to help students
                understand course materials quickly and turn them into revision-friendly outputs.

                Learning scene:
                %s

                Output requirements:
                1. Generate a concise summary focused on course concepts and exam review.
                2. Extract 5 to 8 keywords. Prefer concepts, methods, formulas, models, and domain terms.
                3. Extract 4 to 8 keyPoints. Each point should be standalone and easy to study.
                4. Generate chapterHighlights grouped by chapter or topic. Each group should contain 2 to 4 highlights.
                5. Generate reviewOutline as a practical revision checklist for students.
                6. If the source comes from lecture audio or video, the source may be informal,
                   but the result must still be suitable for structured studying.
                7. Keep the output structured, concise, and student-oriented.

                Chapter clues:
                %s

                Cleaned study material:
                %s
                """.formatted(
                learningScene,
                chapterInfo.isEmpty() ? "No explicit chapters were detected. Group by topic if needed." : String.join(" | ", chapterInfo),
                materialContent.getCleanedText());
    }

    private String resolveLearningScene(String materialType) {
        MaterialTypeEnum materialTypeEnum = MaterialTypeEnum.valueOf(materialType);
        return switch (materialTypeEnum) {
            case DOCUMENT, PPT, TEXT -> "Course handout / lecture notes / reading material";
            case AUDIO -> "Lecture audio / spoken explanation";
            case VIDEO -> "Recorded lecture / teaching video";
        };
    }
}
