package com.studyflow.ai.gateway;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.enums.ResultCodeEnum;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class LangChain4jEmbeddingGateway implements EmbeddingGateway {

    private static final int DEFAULT_EMBEDDING_BATCH_SIZE = 10;

    private final EmbeddingModel embeddingModel;

    private final int embeddingBatchSize;

    public LangChain4jEmbeddingGateway(EmbeddingModel embeddingModel) {
        this(embeddingModel, DEFAULT_EMBEDDING_BATCH_SIZE);
    }

    public LangChain4jEmbeddingGateway(EmbeddingModel embeddingModel, Integer embeddingBatchSize) {
        this.embeddingModel = embeddingModel;
        this.embeddingBatchSize = Math.max(1, embeddingBatchSize == null ? DEFAULT_EMBEDDING_BATCH_SIZE : embeddingBatchSize);
    }

    @Override
    public List<List<Double>> embedDocuments(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<TextSegment> textSegments = texts.stream()
                .map(this::toSafeText)
                .map(TextSegment::from)
                .toList();
        List<List<Double>> vectors = new ArrayList<>(textSegments.size());
        for (int start = 0; start < textSegments.size(); start += embeddingBatchSize) {
            int end = Math.min(start + embeddingBatchSize, textSegments.size());
            List<TextSegment> batch = textSegments.subList(start, end);
            Response<List<Embedding>> response = embeddingModel.embedAll(batch);
            if (response == null || response.content() == null || response.content().size() != batch.size()) {
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                        "langchain4j embedding response is invalid");
            }
            response.content().stream()
                    .map(this::toDoubleList)
                    .forEach(vectors::add);
        }
        return vectors;
    }

    @Override
    public List<Double> embedQuery(String text) {
        Response<Embedding> response = embeddingModel.embed(toSafeText(text));
        if (response == null || response.content() == null) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "langchain4j query embedding response is invalid");
        }
        return toDoubleList(response.content());
    }

    private String toSafeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private List<Double> toDoubleList(Embedding embedding) {
        List<Float> values = embedding.vectorAsList();
        List<Double> result = new ArrayList<>(values.size());
        for (Float value : values) {
            result.add(value == null ? 0.0D : value.doubleValue());
        }
        return result;
    }
}
