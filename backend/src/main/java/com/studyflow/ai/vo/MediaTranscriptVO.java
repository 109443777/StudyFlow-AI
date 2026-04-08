package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaTranscriptVO {

    private Long id;

    private Long materialId;

    private String mediaType;

    private String transcriptText;

    private List<TranscriptSegmentVO> transcriptSegments;

    private Long duration;

    private String transcriptStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
