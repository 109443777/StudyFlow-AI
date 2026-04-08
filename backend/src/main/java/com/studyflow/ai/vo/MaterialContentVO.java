package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialContentVO {

    private Long id;

    private Long materialId;

    private String contentType;

    private String rawText;

    private String cleanedText;

    private List<String> chapterInfo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
