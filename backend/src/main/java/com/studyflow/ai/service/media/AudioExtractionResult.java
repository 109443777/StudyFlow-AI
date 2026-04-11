package com.studyflow.ai.service.media;

import java.nio.file.Path;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioExtractionResult {

    private Path audioFilePath;
}
