package com.studyflow.ai.service;

import com.studyflow.ai.service.media.AudioExtractionResult;
import java.nio.file.Path;

public interface MediaAudioExtractService {

    AudioExtractionResult extractToWav(Path videoPath, Long materialId);
}
