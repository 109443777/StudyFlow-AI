package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.entity.MaterialChunk;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.EmbeddingGateway;
import com.studyflow.ai.mapper.MaterialChunkMapper;
import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.vector.ChunkSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("'${studyflow.vector-store.provider:database}' == 'database' || '${studyflow.vector-store.fallback-to-database:true}' == 'true'")
@RequiredArgsConstructor
public class DatabaseVectorStoreServiceImpl implements VectorStoreService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MaterialChunkMapper materialChunkMapper;

    private final EmbeddingGateway embeddingGateway;

    @Override
    public void upsertMaterialChunks(Long materialId, List<MaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_CONTENT_NOT_FOUND);
        }
        List<List<Double>> vectors = embeddingGateway.embedDocuments(chunks.stream().map(MaterialChunk::getChunkText).toList());
        for (int i = 0; i < chunks.size(); i++) {
            MaterialChunk chunk = chunks.get(i);
            chunk.setEmbeddingVector(writeVector(vectors.get(i)));
            materialChunkMapper.updateById(chunk);
        }
    }

    @Override
    public List<ChunkSearchResult> searchByMaterialId(Long materialId, String question, Integer topK) {
        return searchByMaterialIds(List.of(materialId), question, topK);
    }

    @Override
    public List<ChunkSearchResult> searchByMaterialIds(List<Long> materialIds, String question, Integer topK) {
        List<MaterialChunk> chunks = materialChunkMapper.selectList(new LambdaQueryWrapper<MaterialChunk>()
                .in(MaterialChunk::getMaterialId, materialIds)
                .orderByAsc(MaterialChunk::getMaterialId)
                .orderByAsc(MaterialChunk::getChunkIndex));
        if (chunks.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.VECTOR_INDEX_NOT_READY);
        }
        List<Double> queryVector = embeddingGateway.embedQuery(question);
        List<ChunkSearchResult> results = new ArrayList<>();
        for (MaterialChunk chunk : chunks) {
            if (chunk.getEmbeddingVector() == null || chunk.getEmbeddingVector().isBlank()) {
                continue;
            }
            double score = cosineSimilarity(queryVector, readVector(chunk.getEmbeddingVector()));
            results.add(ChunkSearchResult.builder()
                    .chunk(chunk)
                    .score(score)
                    .build());
        }
        if (results.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.VECTOR_INDEX_NOT_READY);
        }
        return results.stream()
                .sorted(Comparator.comparing(ChunkSearchResult::getScore).reversed())
                .limit(topK == null ? 4 : topK)
                .toList();
    }

    private String writeVector(List<Double> vector) {
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to serialize embedding vector");
        }
    }

    private List<Double> readVector(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Double>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to deserialize embedding vector");
        }
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < size; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
