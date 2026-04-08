package com.studyflow.ai.service;

import com.studyflow.ai.dto.MaterialContentQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;
import java.util.List;

public interface MaterialContentService {

    MaterialContent parseAndSave(Material material);

    MaterialContent saveOrUpdatePlainText(Long materialId, String rawText, String cleanedText, List<String> chapterInfo);

    MaterialContent getByMaterialId(Long userId, MaterialContentQueryDTO materialContentQueryDTO);
}
