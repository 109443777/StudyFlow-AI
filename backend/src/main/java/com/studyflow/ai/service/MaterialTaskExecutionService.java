package com.studyflow.ai.service;

import com.studyflow.ai.entity.Material;
import com.studyflow.ai.enums.ParseTaskTypeEnum;

public interface MaterialTaskExecutionService {

    void execute(Material material, ParseTaskTypeEnum taskType);
}
