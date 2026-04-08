package com.studyflow.ai.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class LangChain4jAiGateway implements AiGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ChatLanguageModel chatLanguageModel;

    public LangChain4jAiGateway(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public StudyContentAnalysisResult analyzeStudyContent(AiStudyContentRequest request) {
        String rawResponse = chatLanguageModel.generate(buildStudyAnalysisPrompt(request));
        try {
            StudyContentAnalysisResult result = OBJECT_MAPPER.readValue(
                    extractJsonPayload(rawResponse),
                    StudyContentAnalysisResult.class);
            return normalize(result);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse AI study analysis response, materialId={}, rawResponse={}",
                    request.getMaterialId(), abbreviate(rawResponse), exception);
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR,
                    "failed to parse langchain4j ai analysis response");
        }
    }

    @Override
    public String answer(String question, List<String> contexts) {
        String answer = chatLanguageModel.generate(buildRagAnswerPrompt(question, contexts));
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "langchain4j ai returned an empty answer");
        }
        return answer.trim();
    }

    private String buildStudyAnalysisPrompt(AiStudyContentRequest request) {
        return """
                %s

                Return ONLY valid JSON with the exact structure below.
                Do not add markdown fences, explanations, or any extra text.
                {
                  "summary": "string",
                  "keywords": ["string"],
                  "keyPoints": ["string"],
                  "chapterHighlights": [
                    {
                      "chapterTitle": "string",
                      "highlights": ["string"]
                    }
                  ],
                  "reviewOutline": ["string"]
                }

                Requirements:
                - All fields must exist.
                - Use concise, revision-friendly language.
                - keywords should contain 5 to 8 items.
                - keyPoints should contain 4 to 8 items.
                - chapterHighlights should group the material by chapter or topic when possible.
                - reviewOutline should be a practical student revision checklist.
                """.formatted(request.getPrompt());
    }

    private String buildRagAnswerPrompt(String question, List<String> contexts) {
        List<String> safeContexts = contexts == null ? List.of() : contexts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        String contextBlock = safeContexts.isEmpty()
                ? "No retrieved material context was provided."
                : String.join("\n", safeContexts);
        String safeQuestion = question == null ? "" : question.trim();
        return """
                You are StudyFlow AI, a learning assistant for university students.
                Answer the student's question strictly based on the retrieved study material.
                If the material is insufficient, say that clearly instead of inventing facts.
                Keep the answer concise, accurate, and useful for course revision.

                Retrieved study material:
                %s

                Student question and recent conversation context:
                %s
                """.formatted(contextBlock, safeQuestion);
    }

    private StudyContentAnalysisResult normalize(StudyContentAnalysisResult result) {
        if (result == null) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR,
                    "langchain4j ai returned empty structured analysis");
        }
        result.setSummary(defaultString(result.getSummary()));
        result.setKeywords(normalizeStringList(result.getKeywords()));
        result.setKeyPoints(normalizeStringList(result.getKeyPoints()));
        result.setReviewOutline(normalizeStringList(result.getReviewOutline()));
        result.setChapterHighlights(normalizeChapterHighlights(result.getChapterHighlights()));
        return result;
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<ChapterHighlight> normalizeChapterHighlights(List<ChapterHighlight> chapterHighlights) {
        if (chapterHighlights == null) {
            return List.of();
        }
        return chapterHighlights.stream()
                .filter(Objects::nonNull)
                .map(item -> ChapterHighlight.builder()
                        .chapterTitle(StringUtils.hasText(item.getChapterTitle()) ? item.getChapterTitle().trim() : "Topic")
                        .highlights(normalizeStringList(item.getHighlights()))
                        .build())
                .toList();
    }

    private String extractJsonPayload(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "langchain4j ai returned an empty structured response");
        }
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > -1 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR,
                    "langchain4j ai response is not valid json");
        }
        return trimmed.substring(start, end + 1);
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 400 ? value : value.substring(0, 400) + "...";
    }
}
