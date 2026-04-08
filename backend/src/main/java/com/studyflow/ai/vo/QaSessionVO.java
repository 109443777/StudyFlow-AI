package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaSessionVO {

    private Long id;

    private Long materialId;

    private String sessionName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
