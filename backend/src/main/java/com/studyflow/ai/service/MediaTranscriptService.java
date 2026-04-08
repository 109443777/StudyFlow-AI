package com.studyflow.ai.service;

import com.studyflow.ai.dto.MediaTranscriptQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MediaTranscript;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.service.media.TranscriptSegment;
import java.util.List;

public interface MediaTranscriptService {

    MediaTranscript transcribeAndSave(Material material, ParseTaskTypeEnum taskType);

    MediaTranscript getByMaterialId(Long userId, MediaTranscriptQueryDTO mediaTranscriptQueryDTO);

    List<TranscriptSegment> readSegments(String transcriptSegments);
}
