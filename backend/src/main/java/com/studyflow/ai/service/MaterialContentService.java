package com.studyflow.ai.service;

import com.studyflow.ai.dto.MaterialContentQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialContent;

public interface MaterialContentService {

    MaterialContent parseAndSave(Material material);

    MaterialContent getByMaterialId(Long userId, MaterialContentQueryDTO materialContentQueryDTO);
}
