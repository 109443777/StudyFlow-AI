package com.studyflow.ai.gateway;

import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import com.studyflow.ai.service.media.TranscriptSegment;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MockMediaTranscriptionGateway implements MediaTranscriptionGateway {

    private final TranscriptionProperties transcriptionProperties;

    @Override
    public MediaTranscriptionResult transcribe(MediaTranscriptionRequest request) {
        long duration = Math.max(5_000L, (long) request.getContent().length * 20L);
        String preview = buildPreview(request.getContent());
        String transcriptText = "%s %s material %d converted to transcript. %s"
                .formatted(
                        transcriptionProperties.getMockPrefix(),
                        request.getMediaType().name(),
                        request.getMaterialId(),
                        preview);
        List<TranscriptSegment> segments = List.of(
                TranscriptSegment.builder()
                        .index(0)
                        .startMillis(0L)
                        .endMillis(duration / 2)
                        .text(transcriptText + " Segment 1.")
                        .build(),
                TranscriptSegment.builder()
                        .index(1)
                        .startMillis(duration / 2)
                        .endMillis(duration)
                        .text(transcriptText + " Segment 2.")
                        .build());
        return MediaTranscriptionResult.builder()
                .transcriptText(segments.stream().map(TranscriptSegment::getText).reduce((a, b) -> a + "\n" + b).orElse(transcriptText))
                .transcriptSegments(segments)
                .duration(duration)
                .build();
    }

    private String buildPreview(byte[] content) {
        String rawPreview = new String(content, StandardCharsets.UTF_8).trim();
        if (rawPreview.isBlank()) {
            return "No embedded preview content was detected in the media bytes.";
        }
        String normalized = rawPreview.replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }
}
