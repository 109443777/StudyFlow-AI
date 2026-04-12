package com.studyflow.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaSessionMaterialVO {

    private Long id;

    private String fileName;

    private String materialType;

    private String parseStatus;
}
