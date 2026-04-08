package com.studyflow.ai.service.media;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaTranscriptionResult {

    private String transcriptText;

    private List<TranscriptSegment> transcriptSegments;

    private Long duration;
}
