package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.idempotency.IdempotencyGuard;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.exception.NonRetryableTaskException;
import com.studyflow.ai.dto.ParseTaskQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.FileAsset;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.FileAssetMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mq.DeadLetterMessagePublisher;
import com.studyflow.ai.mq.DeadLetterTaskMessage;
import com.studyflow.ai.mq.ParseTaskMessage;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.MaterialTaskExecutionService;
import com.studyflow.ai.service.ParseTaskService;
import com.studyflow.ai.service.TaskFailureRecordService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseTaskServiceImpl implements ParseTaskService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_TASK_LOCK_SECONDS = 15L;
    private static final long PARSE_REQUEST_LOCK_SECONDS = 10L;

    private final ParseTaskMapper parseTaskMapper;

    private final FileAssetMapper fileAssetMapper;

    private final MaterialMapper materialMapper;

    private final ParseTaskMessagePublisher parseTaskMessagePublisher;

    private final DeadLetterMessagePublisher deadLetterMessagePublisher;

    private final MaterialTaskExecutionService materialTaskExecutionService;

    private final IdempotencyGuard idempotencyGuard;

    private final TaskFailureRecordService taskFailureRecordService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAndDispatchInitialTask(Material material) {
        String lockKey = buildInitialTaskLockKey(material.getId());
        if (!idempotencyGuard.tryAcquire(lockKey, INITIAL_TASK_LOCK_SECONDS)) {
            log.info("Skip duplicate initial parse task creation, materialId={}", material.getId());
            return;
        }
        try {
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
                log.info("Created initial parse task, materialId={}, taskId={}, taskType={}",
                        material.getId(), parseTask.getId(), initialTaskType);
            } else if (ParseTaskStatusEnum.QUEUED.name().equals(parseTask.getStatus())
                    || ParseTaskStatusEnum.RUNNING.name().equals(parseTask.getStatus())) {
                log.info("Initial parse task already queued or running, skip duplicate dispatch, materialId={}, taskId={}, taskType={}, status={}",
                        material.getId(), parseTask.getId(), initialTaskType, parseTask.getStatus());
                return;
            } else if (!ParseTaskStatusEnum.SUCCESS.name().equals(parseTask.getStatus())) {
                parseTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
                parseTask.setFailReason(null);
                parseTaskMapper.updateById(parseTask);
                log.info("Re-queued existing initial parse task, materialId={}, taskId={}, taskType={}, previousStatus={}",
                        material.getId(), parseTask.getId(), initialTaskType, parseTask.getStatus());
            } else {
                log.info("Initial parse task already succeeded, skip dispatch, materialId={}, taskId={}, taskType={}",
                        material.getId(), parseTask.getId(), initialTaskType);
                return;
            }
            publishTask(parseTask, initialTaskType);
        } finally {
            idempotencyGuard.release(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParseTask requestInitialParse(Long userId, Long materialId) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialId)
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        String lockKey = buildParseRequestLockKey(materialId);
        if (!idempotencyGuard.tryAcquire(lockKey, PARSE_REQUEST_LOCK_SECONDS)) {
            throw new BusinessException(ResultCodeEnum.CONFLICT, "duplicate parse request, please retry later");
        }
        try {
            ParseTaskTypeEnum initialTaskType = resolveInitialTaskType(material.getMaterialType());
            ParseTask existingTask = findExistingTask(materialId, initialTaskType);
            if (existingTask != null && (ParseTaskStatusEnum.QUEUED.name().equals(existingTask.getStatus())
                    || ParseTaskStatusEnum.RUNNING.name().equals(existingTask.getStatus()))) {
                throw new BusinessException(ResultCodeEnum.CONFLICT, "parse task is already queued or running");
            }
            if (existingTask != null && ParseTaskStatusEnum.SUCCESS.name().equals(existingTask.getStatus())
                    && MaterialParseStatusEnum.SUCCESS.name().equals(material.getParseStatus())) {
                throw new BusinessException(ResultCodeEnum.CONFLICT, "material has already been parsed");
            }
            Material updateMaterial = new Material();
            updateMaterial.setId(material.getId());
            updateMaterial.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
            materialMapper.updateById(updateMaterial);
            createAndDispatchInitialTask(materialMapper.selectById(materialId));
            ParseTask latestTask = findExistingTask(materialId, initialTaskType);
            if (latestTask == null) {
                throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to dispatch parse task");
            }
            return latestTask;
        } finally {
            idempotencyGuard.release(lockKey);
        }
    }

    @Override
    public void processTask(Long taskId) {
        ParseTask parseTask = getTaskById(taskId);
        if (ParseTaskStatusEnum.SUCCESS.name().equals(parseTask.getStatus())) {
            log.info("Skip parse task processing because task already succeeded, taskId={}", taskId);
            return;
        }
        Material material = materialMapper.selectById(parseTask.getMaterialId());
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        log.info("Start processing parse task, taskId={}, materialId={}, taskType={}, retryCount={}",
                parseTask.getId(), parseTask.getMaterialId(), parseTask.getTaskType(), parseTask.getRetryCount());
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
            log.info("Completed parse task successfully, taskId={}, materialId={}, taskType={}",
                    parseTask.getId(), parseTask.getMaterialId(), parseTask.getTaskType());
        } catch (NonRetryableTaskException exception) {
            handleTaskFailure(parseTask, material, exception.getMessage(), false);
        } catch (Exception exception) {
            handleTaskFailure(parseTask, material, exception.getMessage(), true);
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

    private void handleTaskFailure(ParseTask parseTask, Material material, String failReason, boolean retryable) {
        int nextRetryCount = (parseTask.getRetryCount() == null ? 0 : parseTask.getRetryCount()) + 1;
        ParseTask updateTask = new ParseTask();
        updateTask.setId(parseTask.getId());
        updateTask.setRetryCount(nextRetryCount);
        updateTask.setFailReason(failReason);
        if (retryable && nextRetryCount < MAX_RETRY_COUNT) {
            updateTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
            parseTaskMapper.updateById(updateTask);
            log.warn("Parse task execution failed, will retry, taskId={}, materialId={}, taskType={}, retryCount={}, failReason={}",
                    parseTask.getId(), material.getId(), parseTask.getTaskType(), nextRetryCount, failReason);
            publishTask(getTaskById(parseTask.getId()), ParseTaskTypeEnum.valueOf(parseTask.getTaskType()));
        } else {
            updateTask.setStatus(ParseTaskStatusEnum.FAILED.name());
            updateTask.setEndTime(LocalDateTime.now());
            parseTaskMapper.updateById(updateTask);

            Material updateMaterial = new Material();
            updateMaterial.setId(material.getId());
            updateMaterial.setParseStatus(MaterialParseStatusEnum.FAILED.name());
            materialMapper.updateById(updateMaterial);
            ParseTask failedTask = getTaskById(parseTask.getId());
            taskFailureRecordService.recordFailure(failedTask, failReason);
            deadLetterMessagePublisher.publish(DeadLetterTaskMessage.builder()
                    .taskId(failedTask.getId())
                    .materialId(failedTask.getMaterialId())
                    .userId(failedTask.getUserId())
                    .taskType(ParseTaskTypeEnum.valueOf(failedTask.getTaskType()))
                    .retryCount(failedTask.getRetryCount())
                    .failReason(failReason)
                    .build());
            log.error("Parse task execution failed permanently, taskId={}, materialId={}, taskType={}, retryCount={}, retryable={}, failReason={}",
                    parseTask.getId(), material.getId(), parseTask.getTaskType(), nextRetryCount, retryable, failReason);
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
        refreshFileAssetParseStatus(materialId, material.getParseStatus());
    }

    private void refreshFileAssetParseStatus(Long materialId, String parseStatus) {
        Material material = materialMapper.selectById(materialId);
        if (material == null || material.getFileAssetId() == null) {
            return;
        }
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(material.getFileAssetId());
        fileAsset.setParseStatus(parseStatus);
        fileAssetMapper.updateById(fileAsset);
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

    private String buildInitialTaskLockKey(Long materialId) {
        return "studyflow:idempotent:parse:create:" + materialId;
    }

    private String buildParseRequestLockKey(Long materialId) {
        return "studyflow:idempotent:parse:dispatch:" + materialId;
    }
}
