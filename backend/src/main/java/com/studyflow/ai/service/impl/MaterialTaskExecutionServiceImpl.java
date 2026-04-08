package com.studyflow.ai.service.impl;

import com.studyflow.ai.entity.Material;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MaterialTaskExecutionServiceImpl implements MaterialTaskExecutionService {

    @Override
    public void execute(Material material, ParseTaskTypeEnum taskType) {
        log.info("Execute placeholder task, taskType={}, materialId={}, objectKey={}",
                taskType, material.getId(), material.getObjectKey());
    }
}
