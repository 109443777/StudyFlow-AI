package com.studyflow.ai.service.ai;

import com.studyflow.ai.common.util.TextCleanupSupport;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.enums.MaterialTypeEnum;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudyContentPromptBuilder {

    public String buildPrompt(Material material, MaterialContent materialContent) {
        String learningScene = resolveLearningScene(material.getMaterialType());
        List<String> chapterInfo = TextCleanupSupport.readChapterInfo(materialContent.getChapterInfo());
        return """
                你是 StudyFlow AI，一名面向大学生学习场景的智能学习助手。
                你的任务不是撰写企业汇报，而是帮助学生快速理解课程资料，并生成适合复习和考试准备的结构化内容。

                学习场景：
                %s

                输出要求：
                1. 必须使用简体中文输出所有内容。
                2. 生成简洁的资料摘要，重点覆盖课程概念、方法、结论和考试复习价值。
                3. 提取 5 到 8 个关键词，优先选择概念、方法、公式、模型和学科术语。
                4. 提取 4 到 8 个知识点，每个知识点都应能独立阅读和复习。
                5. 按章节或主题生成 chapterHighlights，每组包含 2 到 4 条重点。
                6. 生成 reviewOutline，格式应像学生可直接照着执行的复习提纲或检查清单。
                7. 如果资料来自课堂音频或录播视频，原始表达可能口语化，但输出仍然要结构化、规范化、便于学习。
                8. 输出必须紧扣学习资料，不要编造资料中不存在的结论。

                章节线索：
                %s

                清洗后的学习资料正文：
                %s
                """.formatted(
                learningScene,
                chapterInfo.isEmpty() ? "未检测到明确章节，请按主题进行归纳。" : String.join(" | ", chapterInfo),
                materialContent.getCleanedText());
    }

    private String resolveLearningScene(String materialType) {
        MaterialTypeEnum materialTypeEnum = MaterialTypeEnum.valueOf(materialType);
        return switch (materialTypeEnum) {
            case DOCUMENT, PPT, TEXT -> "课件、讲义、阅读材料或课堂笔记";
            case AUDIO -> "课堂录音、语音讲解或音频资料";
            case VIDEO -> "课程录播、教学视频或课堂演示视频";
        };
    }
}
