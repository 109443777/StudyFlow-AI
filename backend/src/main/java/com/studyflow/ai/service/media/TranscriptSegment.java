package com.studyflow.ai.service.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment {

    private Integer index;

    private Long startMillis;

    private Long endMillis;

    private String text;
}
