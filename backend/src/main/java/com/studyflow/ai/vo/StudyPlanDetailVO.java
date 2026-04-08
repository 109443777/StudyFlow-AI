package com.studyflow.ai.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudyPlanDetailVO {

    private Long id;

    private Long materialId;

    private String planType;

    private String planName;

    private LocalDate examDate;

    private String status;

    private ReviewOutlineContentVO reviewOutline;

    private ExamStudyPlanContentVO examPlan;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
