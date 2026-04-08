package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyflow.ai.gateway.AiStudyContentRequest;
import com.studyflow.ai.gateway.LangChain4jAiGateway;
import com.studyflow.ai.gateway.LangChain4jEmbeddingGateway;
import com.studyflow.ai.service.ai.StudyContentAnalysisResult;
import dev.langchain4j.data.embedding.Embedding;
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
}
