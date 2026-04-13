package com.studyflow.ai.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.util.ChineseTextSupport;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.ai.ChapterHighlight;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class LangChain4jAiGateway implements AiGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final int STREAM_SEGMENT_LENGTH = 28;

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
        return ensureChineseAnswer(answer.trim());
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
                            if (!StringUtils.hasText(token)) {
                                return;
                            }
                            splitStreamingText(token).forEach(streamHandler::onNext);
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

                请仅返回合法 JSON，不要输出 markdown 代码块、解释说明或额外文本。
                所有 value 必须使用简体中文，JSON key 保持下面的英文结构不变：
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

                进一步要求：
                - 所有字段都必须存在。
                - summary 要简洁，适合学生快速理解和考前回顾。
                - keywords 提取 5 到 8 个。
                - keyPoints 提取 4 到 8 个。
                - chapterHighlights 尽量按章节或主题归类。
                - reviewOutline 必须像学生可执行的复习提纲。
                - 不要输出英文答案，除非资料中必须保留英文术语；即便保留术语，也要配中文解释。
                """.formatted(request.getPrompt());
    }

    private String buildRagSystemPrompt() {
        return """
                你是 StudyFlow AI，一名面向大学生学习场景的智能学习助手。
                你的任务是基于检索到的学习资料片段，用简体中文清晰地讲解知识点。
                请优先解释核心概念、方法、结论、公式意义和复习价值，而不是写检索审计报告。
                除非学生明确要求，否则请忽略参考文献、版权声明、页眉页脚、重复片段和解析噪声。
                如果上下文存在噪声或残缺，请先利用有效内容回答，再简要说明不确定之处。
                你必须始终使用简体中文回答，不要输出英文整段回答。
                不要编造资料中不存在的事实。
                """;
    }

    private String buildRagUserPrompt(String question, List<String> contexts) {
        List<String> safeContexts = contexts == null ? List.of() : contexts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        String contextBlock = safeContexts.isEmpty()
                ? "当前没有检索到可用的资料片段。"
                : String.join("\n", safeContexts);
        String safeQuestion = question == null ? "" : question.trim();
        return """
                学习资料片段：
                %s

                学生请求：
                %s

                回答要求：
                1. 必须使用简体中文回答。
                2. 先给出对学生最有帮助的解释或总结，再补充细节。
                3. 如果问题要求总结重点，请用便于复习的表达方式整理。
                4. 如果片段中既有正文也有噪声，请忽略噪声，只使用有意义的内容。
                5. 只有在确实无法确认时，才说明“根据当前资料无法确认”。
                6. 如果片段主要是参考文献或重复内容，请简短说明，不要冗长解释失败原因。
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
                        .chapterTitle(StringUtils.hasText(item.getChapterTitle()) ? item.getChapterTitle().trim() : "主题")
                        .highlights(normalizeStringList(item.getHighlights()))
                        .build())
                .toList();
    }

    private String ensureChineseAnswer(String answer) {
        if (ChineseTextSupport.containsChinese(answer)) {
            return answer;
        }
        String translated = executeTextGeneration(
                () -> chatLanguageModel.generate("""
                        请将下面的学习问答结果改写为自然、准确、简洁的简体中文。
                        如果原文中有必要保留的英文术语，请保留术语并补充中文解释。
                        只返回改写后的中文结果，不要添加任何额外说明。

                        原文：
                        %s
                        """.formatted(answer)),
                "AI answer generation timed out or failed, please retry later");
        return StringUtils.hasText(translated) ? translated.trim() : answer;
    }

    private List<String> splitStreamingText(String token) {
        String normalized = token == null ? "" : token;
        if (normalized.isBlank() || normalized.length() <= STREAM_SEGMENT_LENGTH) {
            return normalized.isBlank() ? List.of() : List.of(normalized);
        }
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            current.append(ch);
            if (shouldSplitCurrentSegment(current, ch)) {
                segments.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    private boolean shouldSplitCurrentSegment(StringBuilder current, char ch) {
        if (current.length() >= STREAM_SEGMENT_LENGTH) {
            return true;
        }
        return switch (ch) {
            case '。', '！', '？', '；', '\n', ',', '，' -> current.length() >= 10;
            default -> false;
        };
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
