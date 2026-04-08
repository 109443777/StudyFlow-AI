package com.studyflow.ai.service;

import com.studyflow.ai.entity.MaterialChunk;
import com.studyflow.ai.service.vector.ChunkSearchResult;
import java.util.List;

public interface VectorStoreService {

    void upsertMaterialChunks(Long materialId, List<MaterialChunk> chunks);

    List<ChunkSearchResult> searchByMaterialId(Long materialId, String question, Integer topK);
}
