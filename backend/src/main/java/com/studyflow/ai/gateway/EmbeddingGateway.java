package com.studyflow.ai.gateway;

import java.util.List;

public interface EmbeddingGateway {

    List<List<Double>> embedDocuments(List<String> texts);

    List<Double> embedQuery(String text);
}
