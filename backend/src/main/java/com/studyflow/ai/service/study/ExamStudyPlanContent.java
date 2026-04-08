package com.studyflow.ai.service.study;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamStudyPlanContent {

    private LocalDate examDate;

    private Integer countdownDays;

    private List<DailyStudyPlan> dailyPlans;

    private List<String> finalTips;
}
