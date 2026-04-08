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
public class DailyStudyPlan {

    private Integer dayIndex;

    private LocalDate studyDate;

    private String theme;

    private List<String> focusTopics;

    private List<String> tasks;
}
