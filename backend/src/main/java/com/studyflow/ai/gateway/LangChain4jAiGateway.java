package com.studyflow.ai.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class LangChain4jAiGateway implements AiGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ChatLanguageModel chatLanguageModel;

    private final StreamingChatLanguageModel streamingChatLanguageModel;

    public LangChain4jAiGateway(
            ChatLanguageModel chatLanguageModel,
            StreamingChatLanguageModel streamingChatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
    }

    @Override
    public StudyContentAnalysisResult analyzeStudyContent(AiStudyContentRequest request) {
        String rawResponse = executeTextGeneration(
                () -> chatLanguageModel.generate(buildStudyAnalysisPrompt(request)),
                "AI study content generation timed out or failed, please retry later");
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
        Response<AiMessage> response = executeResponseGeneration(
                () -> chatLanguageModel.generate(List.of(
                        SystemMessage.from(buildRagSystemPrompt()),
                        UserMessage.from(buildRagUserPrompt(question, contexts)))),
                "AI answer generation timed out or failed, please retry later");
        String answer = response == null || response.content() == null ? null : response.content().text();
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "langchain4j ai returned an empty answer");
        }
        return answer.trim();
    }

    @Override
    public void streamAnswer(String question, List<String> contexts, AiAnswerStreamHandler streamHandler) {
        try {
            streamingChatLanguageModel.generate(
                    List.of(
                            SystemMessage.from(buildRagSystemPrompt()),
                            UserMessage.from(buildRagUserPrompt(question, contexts))),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            streamHandler.onNext(token);
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            streamHandler.onComplete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.warn("LangChain4j streaming answer generation failed: {}", error.getMessage(), error);
                            streamHandler.onError(new BusinessException(
                                    ResultCodeEnum.SYSTEM_BUSY,
                                    "AI answer generation timed out or failed, please retry later"));
                        }
                    });
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("LangChain4j streaming answer generation failed: {}", exception.getMessage(), exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "AI answer generation timed out or failed, please retry later");
        }
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

    private String buildRagSystemPrompt() {
        return """
                You are StudyFlow AI, a learning assistant for university students.
                Your job is to teach the student clearly, using only the retrieved study material.
                Prefer explaining the topic in a study-friendly way instead of giving a retrieval audit report.
                Focus on the document's core ideas, definitions, methods, conclusions, and revision value.
                Ignore references, copyright notices, page headers, page footers, repeated fragments, and noisy extraction artifacts unless the student explicitly asks about them.
                If some context is noisy or incomplete, still answer from the useful parts first, then briefly state what remains uncertain.
                Do not invent facts outside the retrieved material.
                """;
    }

    private String buildRagUserPrompt(String question, List<String> contexts) {
        List<String> safeContexts = contexts == null ? List.of() : contexts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        String contextBlock = safeContexts.isEmpty()
                ? "No retrieved material context was provided."
                : String.join("\n", safeContexts);
        String safeQuestion = question == null ? "" : question.trim();
        return """
                Study material excerpts:
                %s

                Student request:
                %s

                Answer requirements:
                1. Start with the most likely useful explanation or summary from the valid material content.
                2. When the question asks for key points, summarize them in a student-friendly way.
                3. If the retrieved content is partially noisy, ignore the noisy parts and use the meaningful parts.
                4. Only mention insufficiency after you have extracted whatever can be confirmed from the material.
                5. If the excerpts mainly contain references or repeated fragments, say that briefly and avoid over-explaining the failure.
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

    private String executeTextGeneration(TextGenerationAction action, String failureMessage) {
        try {
            return action.execute();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("LangChain4j text generation failed: {}", exception.getMessage(), exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, failureMessage);
        }
    }

    private Response<AiMessage> executeResponseGeneration(ResponseGenerationAction action, String failureMessage) {
        try {
            return action.execute();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("LangChain4j response generation failed: {}", exception.getMessage(), exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, failureMessage);
        }
    }

    @FunctionalInterface
    private interface TextGenerationAction {
        String execute();
    }

    @FunctionalInterface
    private interface ResponseGenerationAction {
        Response<AiMessage> execute();
    }
}
