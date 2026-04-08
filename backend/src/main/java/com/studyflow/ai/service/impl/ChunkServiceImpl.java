package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.util.TextChunkSupport;
import com.studyflow.ai.config.RagProperties;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialChunk;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.mapper.MaterialChunkMapper;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.service.ChunkService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {

    private final MaterialContentMapper materialContentMapper;

    private final MaterialChunkMapper materialChunkMapper;

    private final RagProperties ragProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MaterialChunk> chunkAndSave(Material material) {
        MaterialContent materialContent = materialContentMapper.selectOne(new LambdaQueryWrapper<MaterialContent>()
                .eq(MaterialContent::getMaterialId, material.getId())
                .last("limit 1"));
        if (materialContent == null || materialContent.getCleanedText() == null || materialContent.getCleanedText().isBlank()) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_CONTENT_NOT_FOUND);
        }
        materialChunkMapper.delete(new LambdaQueryWrapper<MaterialChunk>()
                .eq(MaterialChunk::getMaterialId, material.getId()));

        List<String> chunkTexts = TextChunkSupport.split(
                materialContent.getCleanedText(),
                ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap());
        List<MaterialChunk> chunks = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            MaterialChunk chunk = new MaterialChunk();
            chunk.setMaterialId(material.getId());
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkTexts.get(i));
            chunk.setTokenCount(TextChunkSupport.estimateTokenCount(chunkTexts.get(i)));
            materialChunkMapper.insert(chunk);
            chunks.add(chunk);
        }
        return chunks;
    }

    @Override
    public List<MaterialChunk> listByMaterialId(Long materialId) {
        return materialChunkMapper.selectList(new LambdaQueryWrapper<MaterialChunk>()
                .eq(MaterialChunk::getMaterialId, materialId)
                .orderByAsc(MaterialChunk::getChunkIndex));
    }
}
