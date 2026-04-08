package com.studyflow.ai.service;

import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialChunk;
import java.util.List;

public interface MaterialEmbeddingService {

    List<MaterialChunk> buildIndex(Material material);
}
