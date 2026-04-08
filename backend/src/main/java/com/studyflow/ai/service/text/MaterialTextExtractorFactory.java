package com.studyflow.ai.service.text;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.enums.ResultCodeEnum;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaterialTextExtractorFactory {

    private final List<MaterialTextExtractor> extractors;

    public MaterialTextExtractor getExtractor(Material material) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(material.getFileType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.UNSUPPORTED_FILE_TYPE,
                        "text extractor not found for file type: " + material.getFileType()));
    }
}
