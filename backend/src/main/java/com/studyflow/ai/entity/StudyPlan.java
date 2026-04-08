package com.studyflow.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_plan")
public class StudyPlan extends BaseEntity {

    private Long userId;

    private Long materialId;

    private String planType;

    private String planName;

    private LocalDate examDate;

    private String planContent;

    private String status;
}
