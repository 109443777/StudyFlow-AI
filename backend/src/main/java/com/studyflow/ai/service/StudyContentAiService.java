package com.studyflow.ai.service;

import com.studyflow.ai.dto.MaterialSummaryQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MaterialSummary;
import com.studyflow.ai.service.ai.ChapterHighlight;
import java.util.List;

public interface StudyContentAiService {

    MaterialSummary analyzeAndSave(Material material);

    MaterialSummary analyzeAndSaveByMaterialId(Long userId, Long materialId);

    MaterialSummary getByMaterialId(Long userId, MaterialSummaryQueryDTO materialSummaryQueryDTO);

    List<String> readStringList(String json);

    List<ChapterHighlight> readChapterHighlights(String json);
}
