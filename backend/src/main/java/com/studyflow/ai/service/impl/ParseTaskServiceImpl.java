package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.dto.ParseTaskQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mq.ParseTaskMessage;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import com.studyflow.ai.service.ParseTaskService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ParseTaskServiceImpl implements ParseTaskService {

    private static final int MAX_RETRY_COUNT = 3;

    private final ParseTaskMapper parseTaskMapper;

    private final MaterialMapper materialMapper;

    private final ParseTaskMessagePublisher parseTaskMessagePublisher;

    private final MaterialTaskExecutionService materialTaskExecutionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAndDispatchInitialTask(Material material) {
        ParseTaskTypeEnum initialTaskType = resolveInitialTaskType(material.getMaterialType());
        if (initialTaskType == null) {
            return;
        }
        ParseTask parseTask = findExistingTask(material.getId(), initialTaskType);
        if (parseTask == null) {
            parseTask = new ParseTask();
            parseTask.setMaterialId(material.getId());
            parseTask.setUserId(material.getUserId());
            parseTask.setTaskType(initialTaskType.name());
            parseTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
            parseTask.setRetryCount(0);
            parseTaskMapper.insert(parseTask);
        } else if (!ParseTaskStatusEnum.SUCCESS.name().equals(parseTask.getStatus())) {
            parseTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
            parseTaskMapper.updateById(parseTask);
        }
        publishTask(parseTask, initialTaskType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTask(Long taskId) {
        ParseTask parseTask = getTaskById(taskId);
        if (ParseTaskStatusEnum.SUCCESS.name().equals(parseTask.getStatus())) {
            return;
        }
        Material material = materialMapper.selectById(parseTask.getMaterialId());
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        try {
            markTaskRunning(parseTask);
            materialTaskExecutionService.execute(material, ParseTaskTypeEnum.valueOf(parseTask.getTaskType()));
            markTaskSuccess(parseTask);
            if (isInitialTask(parseTask)) {
                createAndDispatchFollowUpTask(material, ParseTaskTypeEnum.AI_SUMMARY);
            }
            if (ParseTaskTypeEnum.AI_SUMMARY.name().equals(parseTask.getTaskType())) {
                createAndDispatchFollowUpTask(material, ParseTaskTypeEnum.EMBEDDING);
            }
            refreshMaterialParseStatus(material.getId());
        } catch (Exception exception) {
            handleTaskFailure(parseTask, material, exception.getMessage());
        }
    }

    @Override
    public ParseTask getTaskById(Long taskId) {
        ParseTask parseTask = parseTaskMapper.selectById(taskId);
        if (parseTask == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "parse task not found");
        }
        return parseTask;
    }

    @Override
    public ParseTask getTaskDetail(Long taskId, Long userId) {
        ParseTask parseTask = parseTaskMapper.selectOne(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getId, taskId)
                .eq(ParseTask::getUserId, userId)
                .last("limit 1"));
        if (parseTask == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "parse task not found");
        }
        return parseTask;
    }

    @Override
    public List<ParseTask> listTasks(Long userId, ParseTaskQueryDTO parseTaskQueryDTO) {
        ParseTaskQueryDTO queryDTO = parseTaskQueryDTO == null ? new ParseTaskQueryDTO() : parseTaskQueryDTO;
        LambdaQueryWrapper<ParseTask> queryWrapper = new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getUserId, userId)
                .orderByDesc(ParseTask::getCreateTime);
        if (queryDTO.getMaterialId() != null) {
            queryWrapper.eq(ParseTask::getMaterialId, queryDTO.getMaterialId());
        }
        if (StringUtils.hasText(queryDTO.getTaskType())) {
            queryWrapper.eq(ParseTask::getTaskType, queryDTO.getTaskType().trim().toUpperCase());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            queryWrapper.eq(ParseTask::getStatus, queryDTO.getStatus().trim().toUpperCase());
        }
        return parseTaskMapper.selectList(queryWrapper);
    }

    private void createAndDispatchFollowUpTask(Material material, ParseTaskTypeEnum taskType) {
        ParseTask existingTask = findExistingTask(material.getId(), taskType);
        if (existingTask != null) {
            if (!ParseTaskStatusEnum.SUCCESS.name().equals(existingTask.getStatus())) {
                existingTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
                existingTask.setFailReason(null);
                parseTaskMapper.updateById(existingTask);
                publishTask(existingTask, taskType);
            }
            return;
        }
        ParseTask parseTask = new ParseTask();
        parseTask.setMaterialId(material.getId());
        parseTask.setUserId(material.getUserId());
        parseTask.setTaskType(taskType.name());
        parseTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
        parseTask.setRetryCount(0);
        parseTaskMapper.insert(parseTask);
        publishTask(parseTask, taskType);
    }

    private void publishTask(ParseTask parseTask, ParseTaskTypeEnum taskType) {
        ParseTaskMessage message = ParseTaskMessage.builder()
                .taskId(parseTask.getId())
                .materialId(parseTask.getMaterialId())
                .userId(parseTask.getUserId())
                .taskType(taskType)
                .build();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    parseTaskMessagePublisher.publish(message, taskType);
                }
            });
            return;
        }
        parseTaskMessagePublisher.publish(message, taskType);
    }

    private void markTaskRunning(ParseTask parseTask) {
        ParseTask updateTask = new ParseTask();
        updateTask.setId(parseTask.getId());
        updateTask.setStatus(ParseTaskStatusEnum.RUNNING.name());
        updateTask.setStartTime(parseTask.getStartTime() == null ? LocalDateTime.now() : parseTask.getStartTime());
        updateTask.setFailReason(null);
        parseTaskMapper.updateById(updateTask);

        Material material = new Material();
        material.setId(parseTask.getMaterialId());
        material.setParseStatus(MaterialParseStatusEnum.PARSING.name());
        materialMapper.updateById(material);
    }

    private void markTaskSuccess(ParseTask parseTask) {
        ParseTask updateTask = new ParseTask();
        updateTask.setId(parseTask.getId());
        updateTask.setStatus(ParseTaskStatusEnum.SUCCESS.name());
        updateTask.setEndTime(LocalDateTime.now());
        parseTaskMapper.updateById(updateTask);
    }

    private void handleTaskFailure(ParseTask parseTask, Material material, String failReason) {
        int nextRetryCount = (parseTask.getRetryCount() == null ? 0 : parseTask.getRetryCount()) + 1;
        ParseTask updateTask = new ParseTask();
        updateTask.setId(parseTask.getId());
        updateTask.setRetryCount(nextRetryCount);
        updateTask.setFailReason(failReason);
        if (nextRetryCount < MAX_RETRY_COUNT) {
            updateTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
            parseTaskMapper.updateById(updateTask);
            publishTask(getTaskById(parseTask.getId()), ParseTaskTypeEnum.valueOf(parseTask.getTaskType()));
        } else {
            updateTask.setStatus(ParseTaskStatusEnum.FAILED.name());
            updateTask.setEndTime(LocalDateTime.now());
            parseTaskMapper.updateById(updateTask);

            Material updateMaterial = new Material();
            updateMaterial.setId(material.getId());
            updateMaterial.setParseStatus(MaterialParseStatusEnum.FAILED.name());
            materialMapper.updateById(updateMaterial);
        }
    }

    private void refreshMaterialParseStatus(Long materialId) {
        List<ParseTask> tasks = parseTaskMapper.selectList(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getMaterialId, materialId));
        if (tasks.isEmpty()) {
            return;
        }
        boolean hasFailed = tasks.stream().anyMatch(task -> ParseTaskStatusEnum.FAILED.name().equals(task.getStatus()));
        Material material = new Material();
        material.setId(materialId);
        if (hasFailed) {
            material.setParseStatus(MaterialParseStatusEnum.FAILED.name());
        } else {
            boolean allSuccess = tasks.stream().allMatch(task -> ParseTaskStatusEnum.SUCCESS.name().equals(task.getStatus()));
            material.setParseStatus(allSuccess ? MaterialParseStatusEnum.SUCCESS.name() : MaterialParseStatusEnum.PARSING.name());
        }
        materialMapper.updateById(material);
    }

    private ParseTask findExistingTask(Long materialId, ParseTaskTypeEnum taskType) {
        return parseTaskMapper.selectOne(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getMaterialId, materialId)
                .eq(ParseTask::getTaskType, taskType.name())
                .last("limit 1"));
    }

    private ParseTaskTypeEnum resolveInitialTaskType(String materialType) {
        if (materialType == null) {
            return null;
        }
        MaterialTypeEnum typeEnum = MaterialTypeEnum.valueOf(materialType);
        return switch (typeEnum) {
            case DOCUMENT, PPT, TEXT -> ParseTaskTypeEnum.TEXT_PARSE;
            case AUDIO -> ParseTaskTypeEnum.AUDIO_TRANSCRIBE;
            case VIDEO -> ParseTaskTypeEnum.VIDEO_TRANSCRIBE;
        };
    }

    private boolean isInitialTask(ParseTask parseTask) {
        ParseTaskTypeEnum taskType = ParseTaskTypeEnum.valueOf(parseTask.getTaskType());
        return taskType == ParseTaskTypeEnum.TEXT_PARSE
                || taskType == ParseTaskTypeEnum.AUDIO_TRANSCRIBE
                || taskType == ParseTaskTypeEnum.VIDEO_TRANSCRIBE;
    }
}
