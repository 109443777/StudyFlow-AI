package com.studyflow.ai.service;

import com.studyflow.ai.dto.GenerateStudyPlanDTO;
import com.studyflow.ai.dto.StudyPlanQueryDTO;
import com.studyflow.ai.entity.StudyPlan;
import com.studyflow.ai.service.study.ExamStudyPlanContent;
import java.util.List;

public interface StudyPlanService {

    StudyPlan generate(Long userId, GenerateStudyPlanDTO generateStudyPlanDTO);

    StudyPlan getDetail(Long userId, Long planId);

    List<StudyPlan> listPlans(Long userId, StudyPlanQueryDTO studyPlanQueryDTO);

    ExamStudyPlanContent readContent(String json);
}
