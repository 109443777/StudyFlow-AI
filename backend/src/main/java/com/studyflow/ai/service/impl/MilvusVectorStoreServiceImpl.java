package com.studyflow.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.VectorStoreProperties;
import com.studyflow.ai.entity.MaterialChunk;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.EmbeddingGateway;
import com.studyflow.ai.mapper.MaterialChunkMapper;
import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.vector.ChunkSearchResult;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "studyflow.vector-store.provider", havingValue = "milvus")
public class MilvusVectorStoreServiceImpl implements VectorStoreService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String FIELD_CHUNK_ID = "chunk_id";

    private static final String FIELD_MATERIAL_ID = "material_id";

    private static final String FIELD_CHUNK_INDEX = "chunk_index";

    private static final String FIELD_CHUNK_TEXT = "chunk_text";

    private static final String FIELD_EMBEDDING = "embedding";

    private final VectorStoreProperties vectorStoreProperties;

    private final EmbeddingGateway embeddingGateway;

    private final MaterialChunkMapper materialChunkMapper;

    private final ObjectProvider<DatabaseVectorStoreServiceImpl> databaseVectorStoreServiceProvider;

    private volatile MilvusClientV2 milvusClient;

    private volatile boolean collectionReady;

    @Autowired
    public MilvusVectorStoreServiceImpl(
            VectorStoreProperties vectorStoreProperties,
            EmbeddingGateway embeddingGateway,
            MaterialChunkMapper materialChunkMapper,
            ObjectProvider<DatabaseVectorStoreServiceImpl> databaseVectorStoreServiceProvider) {
        this(vectorStoreProperties, embeddingGateway, materialChunkMapper, databaseVectorStoreServiceProvider, null);
    }

    MilvusVectorStoreServiceImpl(
            VectorStoreProperties vectorStoreProperties,
            EmbeddingGateway embeddingGateway,
            MaterialChunkMapper materialChunkMapper,
            ObjectProvider<DatabaseVectorStoreServiceImpl> databaseVectorStoreServiceProvider,
            MilvusClientV2 milvusClient) {
        this.vectorStoreProperties = vectorStoreProperties;
        this.embeddingGateway = embeddingGateway;
        this.materialChunkMapper = materialChunkMapper;
        this.databaseVectorStoreServiceProvider = databaseVectorStoreServiceProvider;
        this.milvusClient = milvusClient;
    }

    @Override
    public void upsertMaterialChunks(Long materialId, List<MaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_CONTENT_NOT_FOUND);
        }
        List<List<Double>> vectors = embeddingGateway.embedDocuments(chunks.stream().map(MaterialChunk::getChunkText).toList());
        validateEmbeddingCount(vectors, chunks.size());
        List<JsonObject> rows = new ArrayList<>();
        int dimension = vectorStoreProperties.getMilvus().getDimension();
        for (int i = 0; i < chunks.size(); i++) {
            MaterialChunk chunk = chunks.get(i);
            List<Double> vector = vectors.get(i);
            chunk.setEmbeddingVector(writeVector(vector));
            materialChunkMapper.updateById(chunk);

            rows.add(toMilvusRow(materialId, chunk, toFloatVector(vector, dimension)));
        }
        try {
            ensureCollection();
            upsertRowsInBatches(rows);
        } catch (RuntimeException exception) {
            collectionReady = false;
            if (Boolean.TRUE.equals(vectorStoreProperties.getFallbackToDatabase())) {
                log.warn("Failed to upsert material chunks into Milvus, keep MySQL vector fallback, materialId={}",
                        materialId, exception);
                return;
            }
            log.warn("Failed to upsert material chunks into Milvus, materialId={}", materialId, exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to upsert chunks into milvus");
        }
    }

    @Override
    public List<ChunkSearchResult> searchByMaterialId(Long materialId, String question, Integer topK) {
        return searchByMaterialIds(List.of(materialId), question, topK);
    }

    @Override
    public List<ChunkSearchResult> searchByMaterialIds(List<Long> materialIds, String question, Integer topK) {
        try {
            ensureCollection();
            List<Float> queryVector = toFloatVector(
                    embeddingGateway.embedQuery(question),
                    vectorStoreProperties.getMilvus().getDimension());
            SearchResp searchResp = milvusClient().search(SearchReq.builder()
                    .collectionName(collectionName())
                    .data(List.of(new FloatVec(queryVector)))
                    .annsField(FIELD_EMBEDDING)
                    .metricType(metricType())
                    .filter(buildMaterialFilter(materialIds))
                    .limit(topK == null ? 4 : topK)
                    .outputFields(List.of(FIELD_CHUNK_ID, FIELD_MATERIAL_ID, FIELD_CHUNK_INDEX, FIELD_CHUNK_TEXT))
                    .build());
            List<ChunkSearchResult> results = toSearchResults(searchResp);
            if (results.isEmpty()) {
                throw new BusinessException(ResultCodeEnum.VECTOR_INDEX_NOT_READY);
            }
            return results;
        } catch (BusinessException exception) {
            return fallbackSearch(materialIds, question, topK, exception);
        } catch (RuntimeException exception) {
            collectionReady = false;
            log.warn("Failed to search material chunks from Milvus, materialIds={}", materialIds, exception);
            return fallbackSearch(materialIds, question, topK, exception);
        }
    }

    public static List<Float> toFloatVector(List<Double> vector, int expectedDimension) {
        if (vector == null || vector.size() != expectedDimension) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "embedding dimension mismatch, expected=" + expectedDimension
                            + ", actual=" + (vector == null ? 0 : vector.size()));
        }
        List<Float> result = new ArrayList<>(vector.size());
        for (Double value : vector) {
            result.add(value == null ? 0.0F : value.floatValue());
        }
        return result;
    }

    public static String buildMaterialFilter(Long materialId) {
        return FIELD_MATERIAL_ID + " == " + materialId;
    }

    public static String buildMaterialFilter(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.BAD_REQUEST, "materialIds cannot be empty");
        }
        if (materialIds.size() == 1) {
            return buildMaterialFilter(materialIds.get(0));
        }
        return FIELD_MATERIAL_ID + " in [" + materialIds.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    public static int batchCount(int itemCount, int batchSize) {
        if (itemCount <= 0) {
            return 0;
        }
        int safeBatchSize = Math.max(1, batchSize);
        return (itemCount + safeBatchSize - 1) / safeBatchSize;
    }

    private List<ChunkSearchResult> toSearchResults(SearchResp searchResp) {
        if (searchResp == null || searchResp.getSearchResults() == null || searchResp.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<ChunkSearchResult> results = new ArrayList<>();
        for (List<SearchResp.SearchResult> group : searchResp.getSearchResults()) {
            for (SearchResp.SearchResult hit : group) {
                Map<String, Object> entity = hit.getEntity();
                MaterialChunk chunk = new MaterialChunk();
                chunk.setId(toLong(entity.get(FIELD_CHUNK_ID)));
                chunk.setMaterialId(toLong(entity.get(FIELD_MATERIAL_ID)));
                chunk.setChunkIndex(toInteger(entity.get(FIELD_CHUNK_INDEX)));
                chunk.setChunkText(String.valueOf(entity.getOrDefault(FIELD_CHUNK_TEXT, "")));
                results.add(ChunkSearchResult.builder()
                        .chunk(chunk)
                        .score(hit.getScore() == null ? 0.0D : hit.getScore().doubleValue())
                        .build());
            }
        }
        return results;
    }

    private void ensureCollection() {
        if (collectionReady) {
            return;
        }
        synchronized (this) {
            if (collectionReady) {
                return;
            }
            try {
                if (milvusClient().hasCollection(HasCollectionReq.builder()
                        .collectionName(collectionName())
                        .build())) {
                    loadCollection();
                    collectionReady = true;
                    return;
                }
                CreateCollectionReq.CollectionSchema schema = milvusClient().createSchema();
                schema.addField(AddFieldReq.builder()
                        .fieldName(FIELD_CHUNK_ID)
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .isPrimaryKey(Boolean.TRUE)
                        .autoID(Boolean.FALSE)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(FIELD_MATERIAL_ID)
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(FIELD_CHUNK_INDEX)
                        .dataType(io.milvus.v2.common.DataType.Int64)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(FIELD_CHUNK_TEXT)
                        .dataType(io.milvus.v2.common.DataType.VarChar)
                        .maxLength(8192)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(FIELD_EMBEDDING)
                        .dataType(io.milvus.v2.common.DataType.FloatVector)
                        .dimension(vectorStoreProperties.getMilvus().getDimension())
                        .build());
                milvusClient().createCollection(CreateCollectionReq.builder()
                        .collectionName(collectionName())
                        .collectionSchema(schema)
                        .indexParams(List.of(vectorIndexParam()))
                        .build());
                loadCollection();
                collectionReady = true;
            } catch (RuntimeException exception) {
                collectionReady = false;
                log.warn("Failed to ensure Milvus collection, collectionName={}", collectionName(), exception);
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to ensure milvus collection");
            }
        }
    }

    private void upsertRowsInBatches(List<JsonObject> rows) {
        int batchSize = batchSize();
        int totalBatches = batchCount(rows.size(), batchSize);
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int fromIndex = batchIndex * batchSize;
            int toIndex = Math.min(rows.size(), fromIndex + batchSize);
            milvusClient().upsert(UpsertReq.builder()
                    .collectionName(collectionName())
                    .data(rows.subList(fromIndex, toIndex))
                    .build());
        }
    }

    private void validateEmbeddingCount(List<List<Double>> vectors, int expectedCount) {
        if (vectors == null || vectors.size() != expectedCount) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY,
                    "embedding count mismatch, expected=" + expectedCount
                            + ", actual=" + (vectors == null ? 0 : vectors.size()));
        }
    }

    private List<ChunkSearchResult> fallbackSearch(
            Long materialId,
            String question,
            Integer topK,
            Exception exception) {
        return fallbackSearch(List.of(materialId), question, topK, exception);
    }

    private List<ChunkSearchResult> fallbackSearch(
            List<Long> materialIds,
            String question,
            Integer topK,
            Exception exception) {
        if (!Boolean.TRUE.equals(vectorStoreProperties.getFallbackToDatabase())
                || databaseVectorStoreServiceProvider == null) {
            throwOriginalMilvusException(exception);
        }
        DatabaseVectorStoreServiceImpl databaseVectorStoreService = databaseVectorStoreServiceProvider.getIfAvailable();
        if (databaseVectorStoreService == null) {
            throwOriginalMilvusException(exception);
        }
        log.warn("Fallback to database vector store for RAG search, materialIds={}", materialIds, exception);
        return databaseVectorStoreService.searchByMaterialIds(materialIds, question, topK);
    }

    private void throwOriginalMilvusException(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to search chunks from milvus");
    }

    private void loadCollection() {
        milvusClient().loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName())
                .sync(Boolean.TRUE)
                .build());
    }

    private String collectionName() {
        return vectorStoreProperties.getMilvus().getCollectionName();
    }

    private int batchSize() {
        Integer batchSize = vectorStoreProperties.getMilvus().getBatchSize();
        return Math.max(1, batchSize == null ? 64 : batchSize);
    }

    private IndexParam vectorIndexParam() {
        return IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .indexType(IndexParam.IndexType.FLAT)
                .metricType(metricType())
                .build();
    }

    private IndexParam.MetricType metricType() {
        String metricType = vectorStoreProperties.getMilvus().getMetricType();
        if (!StringUtils.hasText(metricType)) {
            return IndexParam.MetricType.COSINE;
        }
        return IndexParam.MetricType.valueOf(metricType.trim().toUpperCase());
    }

    private MilvusClientV2 milvusClient() {
        MilvusClientV2 client = milvusClient;
        if (client == null) {
            synchronized (this) {
                client = milvusClient;
                if (client == null) {
                    client = createMilvusClient(vectorStoreProperties);
                    milvusClient = client;
                }
            }
        }
        return client;
    }

    private JsonObject toMilvusRow(Long materialId, MaterialChunk chunk, List<Float> vector) {
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_CHUNK_ID, chunk.getId());
        row.addProperty(FIELD_MATERIAL_ID, materialId);
        row.addProperty(FIELD_CHUNK_INDEX, chunk.getChunkIndex() == null ? 0L : chunk.getChunkIndex().longValue());
        row.addProperty(FIELD_CHUNK_TEXT, chunk.getChunkText());
        JsonArray embedding = new JsonArray();
        for (Float value : vector) {
            embedding.add(value);
        }
        row.add(FIELD_EMBEDDING, embedding);
        return row;
    }

    private String writeVector(List<Double> vector) {
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to serialize embedding vector");
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static MilvusClientV2 createMilvusClient(VectorStoreProperties vectorStoreProperties) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(vectorStoreProperties.getMilvus().getUri());
        if (StringUtils.hasText(vectorStoreProperties.getMilvus().getToken())) {
            builder.token(vectorStoreProperties.getMilvus().getToken());
        }
        return new MilvusClientV2(builder.build());
    }
}
