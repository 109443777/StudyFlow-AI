package com.studyflow.ai.gateway;

import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class MockAiGateway implements AiGateway {

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "into", "then", "than",
            "课程", "内容", "知识", "学习", "我们", "你们", "他们", "以及", "进行", "通过");

    @Override
    public StudyContentAnalysisResult analyzeStudyContent(AiStudyContentRequest request) {
        String cleanedText = request.getCleanedText() == null ? "" : request.getCleanedText().trim();
        List<String> sentences = extractSentences(cleanedText);
        List<String> keyPoints = sentences.stream().limit(5).map(this::toStudyPoint).toList();
        List<String> keywords = extractKeywordsFromText(cleanedText);
        List<ChapterHighlight> chapterHighlights = buildChapterHighlights(sentences, keyPoints);
        List<String> reviewOutline = buildReviewOutline(keyPoints, keywords);
        String summary = sentences.stream()
                .limit(3)
                .reduce((left, right) -> left + " " + right)
                .orElse("暂无可用摘要。");

        return StudyContentAnalysisResult.builder()
                .summary(summary)
                .keywords(keywords)
                .keyPoints(keyPoints)
                .chapterHighlights(chapterHighlights)
                .reviewOutline(reviewOutline)
                .build();
    }

    @Override
    public String answer(String question, List<String> contexts) {
        List<String> safeContexts = contexts == null ? List.of() : contexts.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(3)
                .toList();
        if (safeContexts.isEmpty()) {
            return "无法基于已上传资料回答这个问题，因为当前没有检索到相关上下文。";
        }
        String normalizedQuestion = extractStudentQuestion(question);
        StringBuilder builder = new StringBuilder();
        builder.append("以下回答基于已上传学习资料：\n");
        builder.append("问题：").append(normalizedQuestion).append('\n');
        builder.append("相关资料片段：\n");
        for (int index = 0; index < safeContexts.size(); index++) {
            builder.append(index + 1).append(". ").append(toStudyPoint(safeContexts.get(index))).append('\n');
        }
        builder.append("结论：以上内容均来自检索到的资料片段，可继续结合原文复习。");
        return builder.toString().trim();
    }

    private String extractStudentQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        String marker = "Student question:";
        int index = prompt.lastIndexOf(marker);
        if (index < 0) {
            return prompt.trim();
        }
        return prompt.substring(index + marker.length()).trim();
    }

    private List<String> extractSentences(String text) {
        return Arrays.stream(text.split("(?<=[。！？!?\\.])|\\n"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(8)
                .toList();
    }

    private String toStudyPoint(String sentence) {
        return sentence.length() <= 120 ? sentence : sentence.substring(0, 120) + "...";
    }

    private List<String> extractKeywordsFromText(String text) {
        String[] tokens = TOKEN_SPLIT_PATTERN.split(text.toLowerCase(Locale.ROOT));
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (String token : tokens) {
            String normalized = token.trim();
            if (normalized.length() < 2 || STOP_WORDS.contains(normalized)) {
                continue;
            }
            frequency.put(normalized, frequency.getOrDefault(normalized, 0) + 1);
        }
        return frequency.entrySet().stream()
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<ChapterHighlight> buildChapterHighlights(List<String> sentences, List<String> keyPoints) {
        if (sentences.isEmpty()) {
            return List.of();
        }
        List<ChapterHighlight> results = new ArrayList<>();
        results.add(ChapterHighlight.builder()
                .chapterTitle("整体概览")
                .highlights(keyPoints.stream().limit(3).toList())
                .build());
        if (sentences.size() > 3) {
            results.add(ChapterHighlight.builder()
                    .chapterTitle("延伸重点")
                    .highlights(sentences.stream().skip(3).limit(3).map(this::toStudyPoint).toList())
                    .build());
        }
        return results;
    }

    private List<String> buildReviewOutline(List<String> keyPoints, List<String> keywords) {
        Set<String> outline = new LinkedHashSet<>();
        outline.add("先通读摘要，建立整份资料的知识框架。");
        if (!keywords.isEmpty()) {
            outline.add("重点记忆关键词：" + String.join("、", keywords.stream().limit(5).toList()) + "。");
        }
        if (!keyPoints.isEmpty()) {
            outline.add("按知识点逐条复述，并用自己的话解释核心概念。");
        }
        outline.add("结合课堂例题、作业或实验场景验证是否真正理解。");
        outline.add("最后做一次闭卷回忆，定位薄弱点后回看原文。");
        return outline.stream().toList();
    }
}
