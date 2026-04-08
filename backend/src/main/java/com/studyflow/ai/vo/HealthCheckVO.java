package com.studyflow.ai.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthCheckVO {

    private String application;

    private String status;

    private LocalDateTime timestamp;
}
