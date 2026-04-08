package com.studyflow.ai.dto;

import lombok.Data;

@Data
public class ParseTaskQueryDTO {

    private Long materialId;

    private String taskType;

    private String status;
}
