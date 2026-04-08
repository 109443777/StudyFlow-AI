package com.studyflow.ai.service.impl;

import com.studyflow.ai.entity.Material;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.service.MaterialContentService;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialTaskExecutionServiceImpl implements MaterialTaskExecutionService {

    private final MaterialContentService materialContentService;

    @Override
    public void execute(Material material, ParseTaskTypeEnum taskType) {
        switch (taskType) {
            case TEXT_PARSE -> {
                materialContentService.parseAndSave(material);
                log.info("Material text parsed successfully, materialId={}, fileType={}",
                        material.getId(), material.getFileType());
            }
            case AUDIO_TRANSCRIBE, VIDEO_TRANSCRIBE, AI_SUMMARY, EMBEDDING ->
                    log.info("Execute placeholder task, taskType={}, materialId={}, objectKey={}",
                            taskType, material.getId(), material.getObjectKey());
        }
    }
}
