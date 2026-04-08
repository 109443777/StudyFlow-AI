package com.studyflow.ai.service.impl;

import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialChunk;
import com.studyflow.ai.service.ChunkService;
import com.studyflow.ai.service.MaterialEmbeddingService;
import com.studyflow.ai.service.VectorStoreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialEmbeddingServiceImpl implements MaterialEmbeddingService {

    private final ChunkService chunkService;

    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MaterialChunk> buildIndex(Material material) {
        List<MaterialChunk> chunks = chunkService.chunkAndSave(material);
        vectorStoreService.upsertMaterialChunks(material.getId(), chunks);
        return chunks;
    }
}
