package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.gateway.AiStudyContentRequest;
import com.studyflow.ai.gateway.LangChain4jAiGateway;
import com.studyflow.ai.gateway.LangChain4jEmbeddingGateway;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jGatewayUnitTests {

    @Test
    void shouldParseStructuredStudyContentResponse() {
        ChatLanguageModel chatLanguageModel = messages -> Response.from(AiMessage.from("""
                ```json
                {
                  "summary": "Operating system scheduling summary",
                  "keywords": ["process", "thread", "scheduler"],
                  "keyPoints": ["A process owns resources.", "A thread is the basic execution unit."],
                  "chapterHighlights": [
                    {
                      "chapterTitle": "Scheduling",
                      "highlights": ["Round-robin improves fairness.", "Priority scheduling favors critical tasks."]
                    }
                  ],
                  "reviewOutline": ["Review process and thread differences", "Compare classic scheduling strategies"]
                }
                ```
                """));
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel);

        StudyContentAnalysisResult result = gateway.analyzeStudyContent(AiStudyContentRequest.builder()
                .materialId(1L)
                .prompt("Analyze the material for studying")
                .cleanedText("Operating system scheduling content")
                .build());

        assertThat(result.getSummary()).isEqualTo("Operating system scheduling summary");
        assertThat(result.getKeywords()).containsExactly("process", "thread", "scheduler");
        assertThat(result.getKeyPoints()).hasSize(2);
        assertThat(result.getChapterHighlights()).hasSize(1);
        assertThat(result.getReviewOutline()).hasSize(2);
    }

    @Test
    void shouldConvertLangChainEmbeddingsToDoubleVectors() {
        EmbeddingModel embeddingModel = new FakeEmbeddingModel();
        LangChain4jEmbeddingGateway gateway = new LangChain4jEmbeddingGateway(embeddingModel);

        List<List<Double>> documentEmbeddings = gateway.embedDocuments(List.of("chunk one", "chunk two"));
        List<Double> queryEmbedding = gateway.embedQuery("query");

        assertThat(documentEmbeddings).hasSize(2);
        assertThat(documentEmbeddings.get(0)).containsExactly(1.0D, 0.5D);
        assertThat(documentEmbeddings.get(1)).containsExactly(2.0D, 1.0D);
        assertThat(queryEmbedding).containsExactly(1.0D, 0.5D);
    }

    @Test
    void shouldSplitDocumentEmbeddingRequestsIntoProviderSafeBatches() {
        BatchingAwareEmbeddingModel embeddingModel = new BatchingAwareEmbeddingModel();
        LangChain4jEmbeddingGateway gateway = new LangChain4jEmbeddingGateway(embeddingModel);

        List<String> texts = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            texts.add("chunk-" + index);
        }

        List<List<Double>> documentEmbeddings = gateway.embedDocuments(texts);

        assertThat(documentEmbeddings).hasSize(11);
        assertThat(embeddingModel.getBatchSizes()).containsExactly(10, 1);
        assertThat(documentEmbeddings.get(0)).containsExactly(1.0D, 0.5D);
        assertThat(documentEmbeddings.get(10)).containsExactly(1.0D, 0.5D);
    }

    @Test
    void shouldBuildLearningFocusedRagPromptWithNoiseFilteringGuidance() {
        PromptEchoChatLanguageModel chatLanguageModel = new PromptEchoChatLanguageModel();
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel);

        String answer = gateway.answer(
                """
                You are StudyFlow AI, a learning assistant for university students.
                Material name: knowledge-graph.pdf
                Student question: 请总结这篇资料的核心贡献
                """,
                List.of(
                        "Chunk 1: 这里是正文内容，讨论多智能体知识图谱构建框架。",
                        "Chunk 2: Authorized licensed use limited to: SHENZHEN UNIVERSITY."));

        assertThat(answer).contains("teach the student clearly");
        assertThat(answer).contains("Ignore references, copyright notices, page headers, page footers");
        assertThat(answer).contains("If some context is noisy or incomplete, still answer from the useful parts first");
        assertThat(answer).contains("核心贡献");
        assertThat(answer).contains("Authorized licensed use limited to: SHENZHEN UNIVERSITY.");
    }

    @Test
    void shouldWrapChatTimeoutAsBusinessException() {
        ChatLanguageModel chatLanguageModel = messages -> {
            throw new RuntimeException("java.io.InterruptedIOException: timeout");
        };
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel);

        assertThatThrownBy(() -> gateway.answer("question", List.of("Chunk 1: content")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI answer generation timed out or failed, please retry later")
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCodeEnum.SYSTEM_BUSY.getCode());
    }

    private static class FakeEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = new ArrayList<>(textSegments.size());
            for (int i = 0; i < textSegments.size(); i++) {
                embeddings.add(Embedding.from(new float[] {i + 1.0F, (i + 1.0F) / 2.0F}));
            }
            return Response.from(embeddings);
        }
    }

    private static class BatchingAwareEmbeddingModel implements EmbeddingModel {

        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            batchSizes.add(textSegments.size());
            if (textSegments.size() > 10) {
                throw new IllegalArgumentException("batch size should not be larger than 10");
            }
            List<Embedding> embeddings = new ArrayList<>(textSegments.size());
            for (int i = 0; i < textSegments.size(); i++) {
                embeddings.add(Embedding.from(new float[] {i + 1.0F, (i + 1.0F) / 2.0F}));
            }
            return Response.from(embeddings);
        }

        public List<Integer> getBatchSizes() {
            return batchSizes;
        }
    }

    private static class PromptEchoChatLanguageModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from(messages.toString()));
        }
    }
}
