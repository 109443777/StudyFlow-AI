package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("media_transcript")
public class MediaTranscript extends BaseEntity {

    private Long materialId;

    private String mediaType;

    private String transcriptText;

    private String transcriptSegments;

    private Long duration;

    private String transcriptStatus;
}
