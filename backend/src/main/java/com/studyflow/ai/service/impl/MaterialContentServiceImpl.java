package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.util.TextCleanupSupport;
import com.studyflow.ai.dto.MaterialContentQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.enums.MaterialContentTypeEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialContentMapper;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.service.MaterialContentService;
import com.studyflow.ai.service.text.MaterialTextExtractor;
import com.studyflow.ai.service.text.MaterialTextExtractorFactory;
import com.studyflow.ai.service.text.TextExtractionResult;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialContentServiceImpl implements MaterialContentService {

    private final MaterialContentMapper materialContentMapper;

    private final MaterialMapper materialMapper;

    private final StorageGateway storageGateway;

    private final MaterialTextExtractorFactory materialTextExtractorFactory;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialContent parseAndSave(Material material) {
        MaterialTextExtractor extractor = materialTextExtractorFactory.getExtractor(material);
        try (InputStream inputStream = storageGateway.download(material.getObjectKey())) {
            TextExtractionResult extractionResult = extractor.extract(inputStream, material.getFileName());
            MaterialContent existingContent = materialContentMapper.selectOne(new LambdaQueryWrapper<MaterialContent>()
                    .eq(MaterialContent::getMaterialId, material.getId())
                    .last("limit 1"));
            if (existingContent == null) {
                MaterialContent materialContent = new MaterialContent();
                materialContent.setMaterialId(material.getId());
                materialContent.setContentType(MaterialContentTypeEnum.PLAIN_TEXT.name());
                materialContent.setRawText(extractionResult.getRawText());
                materialContent.setCleanedText(extractionResult.getCleanedText());
                materialContent.setChapterInfo(TextCleanupSupport.writeChapterInfo(extractionResult.getChapterInfo()));
                materialContentMapper.insert(materialContent);
                return materialContent;
            }
            existingContent.setContentType(MaterialContentTypeEnum.PLAIN_TEXT.name());
            existingContent.setRawText(extractionResult.getRawText());
            existingContent.setCleanedText(extractionResult.getCleanedText());
            existingContent.setChapterInfo(TextCleanupSupport.writeChapterInfo(extractionResult.getChapterInfo()));
            materialContentMapper.updateById(existingContent);
            return existingContent;
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to parse material text");
        }
    }

    @Override
    public MaterialContent getByMaterialId(Long userId, MaterialContentQueryDTO materialContentQueryDTO) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialContentQueryDTO.getMaterialId())
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        MaterialContent materialContent = materialContentMapper.selectOne(new LambdaQueryWrapper<MaterialContent>()
                .eq(MaterialContent::getMaterialId, material.getId())
                .last("limit 1"));
        if (materialContent == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_CONTENT_NOT_FOUND);
        }
        return materialContent;
    }
}
