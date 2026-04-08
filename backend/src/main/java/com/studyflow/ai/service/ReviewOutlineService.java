package com.studyflow.ai.service;

import com.studyflow.ai.dto.GenerateReviewOutlineDTO;
import com.studyflow.ai.entity.StudyPlan;
import com.studyflow.ai.service.study.ReviewOutlineContent;

public interface ReviewOutlineService {

    StudyPlan generate(Long userId, GenerateReviewOutlineDTO generateReviewOutlineDTO);

    ReviewOutlineContent readContent(String json);
}
