package com.studyflow.ai.gateway;

import com.studyflow.ai.config.EmbeddingProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MockEmbeddingGateway implements EmbeddingGateway {

    private final int dimension;

    public MockEmbeddingGateway(EmbeddingProperties embeddingProperties) {
        this.dimension = Math.max(16, embeddingProperties.getMockDimension());
    }

    @Override
    public List<List<Double>> embedDocuments(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public List<Double> embedQuery(String text) {
        return embed(text);
    }

    private List<Double> embed(String text) {
        double[] vector = new double[dimension];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return toList(vector);
        }
        String[] tokens = normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int hash = Math.abs(token.hashCode());
            int index = hash % dimension;
            vector[index] += 1.0D;
        }
        normalize(vector);
        return toList(vector);
    }

    private void normalize(double[] vector) {
        double sum = 0.0D;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0.0D) {
            return;
        }
        double scale = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / scale;
        }
    }

    private List<Double> toList(double[] vector) {
        List<Double> values = new ArrayList<>(vector.length);
        for (double value : vector) {
            values.add(value);
        }
        return values;
    }
}
