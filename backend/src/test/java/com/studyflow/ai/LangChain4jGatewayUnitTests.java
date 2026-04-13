package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.gateway.AiAnswerStreamHandler;
import com.studyflow.ai.gateway.AiStudyContentRequest;
import com.studyflow.ai.gateway.LangChain4jAiGateway;
import com.studyflow.ai.gateway.LangChain4jEmbeddingGateway;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LangChain4jGatewayUnitTests {

    @Test
    void shouldParseStructuredStudyContentResponse() {
        ChatLanguageModel chatLanguageModel = messages -> Response.from(AiMessage.from("""
                ```json
                {
                  "summary": "操作系统调度总结",
                  "keywords": ["进程", "线程", "调度器"],
                  "keyPoints": ["进程负责资源管理。", "线程是基本执行单元。"],
                  "chapterHighlights": [
                    {
                      "chapterTitle": "调度算法",
                      "highlights": ["时间片轮转保证公平性。", "优先级调度适合关键任务。"]
                    }
                  ],
                  "reviewOutline": ["复习进程与线程差异", "对比经典调度算法"]
                }
                ```
                """));
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel, new FakeStreamingChatLanguageModel());

        StudyContentAnalysisResult result = gateway.analyzeStudyContent(AiStudyContentRequest.builder()
                .materialId(1L)
                .prompt("请面向学生分析这份学习资料")
                .cleanedText("操作系统调度相关内容")
                .build());

        assertThat(result.getSummary()).isEqualTo("操作系统调度总结");
        assertThat(result.getKeywords()).containsExactly("进程", "线程", "调度器");
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
    void shouldBuildChineseLearningPromptWithNoiseFilteringGuidance() {
        PromptEchoChatLanguageModel chatLanguageModel = new PromptEchoChatLanguageModel();
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel, new FakeStreamingChatLanguageModel());

        String answer = gateway.answer(
                """
                资料名称：knowledge-graph.pdf
                学生问题：请总结这篇资料的核心贡献
                """,
                List.of(
                        "Chunk 1: 这里是正文内容，讨论多智能体知识图谱构建框架。",
                        "Chunk 2: Authorized licensed use limited to: SHENZHEN UNIVERSITY."));

        assertThat(answer).contains("你是 StudyFlow AI");
        assertThat(answer).contains("你必须始终使用简体中文回答");
        assertThat(answer).contains("忽略参考文献、版权声明、页眉页脚");
        assertThat(answer).contains("请总结这篇资料的核心贡献");
        assertThat(answer).contains("这里是正文内容");
    }

    @Test
    void shouldSplitStreamingAnswerIntoMultipleFragmentsForSseRendering() {
        ChatLanguageModel chatLanguageModel = messages -> Response.from(AiMessage.from("ok"));
        StreamingChatLanguageModel streamingChatLanguageModel = (messages, handler) -> {
            handler.onNext("矩阵可以表示线性变换，并且能够用于描述线性方程组的求解过程。理解矩阵乘法、行列式和特征值，是复习线性代数时非常关键的几个重点。");
            handler.onComplete(Response.from(AiMessage.from("done")));
        };
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel, streamingChatLanguageModel);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        gateway.streamAnswer("请解释矩阵的作用", List.of("资料片段"), new AiAnswerStreamHandler() {
            @Override
            public void onNext(String token) {
                chunks.add(token);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError("不应进入错误分支", throwable);
            }
        });

        assertThat(completed.get()).isTrue();
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join("", chunks))
                .isEqualTo("矩阵可以表示线性变换，并且能够用于描述线性方程组的求解过程。理解矩阵乘法、行列式和特征值，是复习线性代数时非常关键的几个重点。");
    }

    @Test
    void shouldWrapChatTimeoutAsBusinessException() {
        ChatLanguageModel chatLanguageModel = messages -> {
            throw new RuntimeException("java.io.InterruptedIOException: timeout");
        };
        LangChain4jAiGateway gateway = new LangChain4jAiGateway(chatLanguageModel, new FakeStreamingChatLanguageModel());

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

    private static class FakeStreamingChatLanguageModel implements StreamingChatLanguageModel {

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            handler.onNext("streaming answer");
            handler.onComplete(Response.from(AiMessage.from("streaming answer")));
        }
    }
}
