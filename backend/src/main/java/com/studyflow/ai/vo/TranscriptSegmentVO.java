package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TranscriptSegmentVO {

    private Integer index;

    private Long startMillis;

    private Long endMillis;

    private String text;
}
