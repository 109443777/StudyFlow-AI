package com.studyflow.ai.service.rag;

import com.studyflow.ai.entity.Material;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagPromptBuilder {

    public String build(Material material, String question, List<String> recentHistory) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前资料名称：").append(material.getFileName()).append('\n');
        builder.append("回答要求：必须使用简体中文，优先面向学生解释概念、方法、重点和复习价值。\n");
        builder.append("如果资料片段不足以支持结论，请明确说明“根据当前资料无法确认”，不要编造。\n");
        if (recentHistory != null && !recentHistory.isEmpty()) {
            builder.append("最近对话历史：\n");
            for (String item : recentHistory) {
                builder.append("- ").append(toChineseHistoryLine(item)).append('\n');
            }
        }
        builder.append("学生问题：").append(question == null ? "" : question.trim());
        return builder.toString();
    }

    private String toChineseHistoryLine(String history) {
        if (!StringUtils.hasText(history)) {
            return "";
        }
        if (history.startsWith("USER: ")) {
            return "学生：" + history.substring("USER: ".length());
        }
        if (history.startsWith("ASSISTANT: ")) {
            return "助手：" + history.substring("ASSISTANT: ".length());
        }
        return history;
    }
}
