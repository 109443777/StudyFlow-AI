package com.studyflow.ai.service.rag;

import com.studyflow.ai.entity.Material;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

    public String build(Material material, String question, List<String> recentHistory) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are StudyFlow AI, a learning assistant for university students.\n");
        builder.append("Answer only from the provided study material excerpts.\n");
        builder.append("If the excerpts are insufficient, state that clearly instead of guessing.\n");
        builder.append("Material name: ").append(material.getFileName()).append('\n');
        if (recentHistory != null && !recentHistory.isEmpty()) {
            builder.append("Recent session history:\n");
            for (String item : recentHistory) {
                builder.append("- ").append(item).append('\n');
            }
        }
        builder.append("Student question: ").append(question);
        return builder.toString();
    }
}
