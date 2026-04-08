package com.studyflow.ai.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DailyStudyPlanVO {

    private Integer dayIndex;

    private LocalDate studyDate;

    private String theme;

    private List<String> focusTopics;

    private List<String> tasks;
}
