package com.studyflow.ai.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamStudyPlanContentVO {

    private LocalDate examDate;

    private Integer countdownDays;

    private List<DailyStudyPlanVO> dailyPlans;

    private List<String> finalTips;
}
