package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaSessionVO {

    private Long id;

    private Long materialId;

    private List<Long> materialIds;

    private List<QaSessionMaterialVO> materials;

    private String sessionName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
