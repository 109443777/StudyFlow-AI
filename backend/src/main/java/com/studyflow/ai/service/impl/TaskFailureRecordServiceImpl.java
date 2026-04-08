package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.dto.TaskFailureQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.TaskFailureRecord;
import com.studyflow.ai.enums.MaterialParseStatusEnum;
import com.studyflow.ai.enums.ParseTaskStatusEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.TaskFailureRecordStatusEnum;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.ParseTaskMapper;
import com.studyflow.ai.mapper.TaskFailureRecordMapper;
import com.studyflow.ai.mq.ParseTaskMessage;
import com.studyflow.ai.mq.ParseTaskMessagePublisher;
import com.studyflow.ai.service.TaskFailureRecordService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TaskFailureRecordServiceImpl implements TaskFailureRecordService {

    private final TaskFailureRecordMapper taskFailureRecordMapper;

    private final ParseTaskMapper parseTaskMapper;

    private final MaterialMapper materialMapper;

    private final ParseTaskMessagePublisher parseTaskMessagePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(ParseTask parseTask, String failReason) {
        TaskFailureRecord existingRecord = taskFailureRecordMapper.selectOne(new LambdaQueryWrapper<TaskFailureRecord>()
                .eq(TaskFailureRecord::getTaskId, parseTask.getId())
                .eq(TaskFailureRecord::getRecordStatus, TaskFailureRecordStatusEnum.OPEN.name())
                .last("limit 1"));
        if (existingRecord == null) {
            TaskFailureRecord record = new TaskFailureRecord();
            record.setTaskId(parseTask.getId());
            record.setMaterialId(parseTask.getMaterialId());
            record.setUserId(parseTask.getUserId());
            record.setTaskType(parseTask.getTaskType());
            record.setRetryCount(parseTask.getRetryCount());
            record.setFailReason(failReason);
            record.setRecordStatus(TaskFailureRecordStatusEnum.OPEN.name());
            taskFailureRecordMapper.insert(record);
            return;
        }
        existingRecord.setRetryCount(parseTask.getRetryCount());
        existingRecord.setFailReason(failReason);
        taskFailureRecordMapper.updateById(existingRecord);
    }

    @Override
    public List<TaskFailureRecord> listFailures(Long userId, TaskFailureQueryDTO taskFailureQueryDTO) {
        TaskFailureQueryDTO queryDTO = taskFailureQueryDTO == null ? new TaskFailureQueryDTO() : taskFailureQueryDTO;
        LambdaQueryWrapper<TaskFailureRecord> queryWrapper = new LambdaQueryWrapper<TaskFailureRecord>()
                .eq(TaskFailureRecord::getUserId, userId)
                .orderByDesc(TaskFailureRecord::getCreateTime);
        if (queryDTO.getMaterialId() != null) {
            queryWrapper.eq(TaskFailureRecord::getMaterialId, queryDTO.getMaterialId());
        }
        if (StringUtils.hasText(queryDTO.getTaskType())) {
            queryWrapper.eq(TaskFailureRecord::getTaskType, queryDTO.getTaskType().trim().toUpperCase());
        }
        if (StringUtils.hasText(queryDTO.getRecordStatus())) {
            queryWrapper.eq(TaskFailureRecord::getRecordStatus, queryDTO.getRecordStatus().trim().toUpperCase());
        }
        return taskFailureRecordMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskFailureRecord compensate(Long userId, Long recordId) {
        TaskFailureRecord record = taskFailureRecordMapper.selectOne(new LambdaQueryWrapper<TaskFailureRecord>()
                .eq(TaskFailureRecord::getId, recordId)
                .eq(TaskFailureRecord::getUserId, userId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "task failure record not found");
        }
        ParseTask parseTask = parseTaskMapper.selectById(record.getTaskId());
        if (parseTask == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "parse task not found");
        }
        parseTask.setStatus(ParseTaskStatusEnum.QUEUED.name());
        parseTask.setFailReason(null);
        parseTask.setRetryCount(0);
        parseTaskMapper.updateById(parseTask);

        Material material = new Material();
        material.setId(parseTask.getMaterialId());
        material.setParseStatus(MaterialParseStatusEnum.UPLOADED.name());
        materialMapper.updateById(material);

        parseTaskMessagePublisher.publish(ParseTaskMessage.builder()
                        .taskId(parseTask.getId())
                        .materialId(parseTask.getMaterialId())
                        .userId(parseTask.getUserId())
                        .taskType(ParseTaskTypeEnum.valueOf(parseTask.getTaskType()))
                        .build(),
                ParseTaskTypeEnum.valueOf(parseTask.getTaskType()));

        record.setRecordStatus(TaskFailureRecordStatusEnum.COMPENSATED.name());
        taskFailureRecordMapper.updateById(record);
        return record;
    }
}
