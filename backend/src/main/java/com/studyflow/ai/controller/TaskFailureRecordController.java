package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.TaskFailureQueryDTO;
import com.studyflow.ai.entity.TaskFailureRecord;
import com.studyflow.ai.service.TaskFailureRecordService;
import com.studyflow.ai.vo.TaskFailureRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task Failure Governance")
@RestController
@RequestMapping("/api/task-failures")
@RequiredArgsConstructor
public class TaskFailureRecordController {

    private final TaskFailureRecordService taskFailureRecordService;

    @LoginRequired
    @Operation(summary = "List task failure records")
    @GetMapping
    public Result<List<TaskFailureRecordVO>> listFailures(@ModelAttribute TaskFailureQueryDTO taskFailureQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(taskFailureRecordService.listFailures(userId, taskFailureQueryDTO).stream()
                .map(this::toVO)
                .toList());
    }

    @LoginRequired
    @Operation(summary = "Compensate failed task")
    @PostMapping("/{recordId}/compensate")
    public Result<TaskFailureRecordVO> compensate(@PathVariable Long recordId) {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(toVO(taskFailureRecordService.compensate(userId, recordId)));
    }

    private TaskFailureRecordVO toVO(TaskFailureRecord record) {
        return TaskFailureRecordVO.builder()
                .id(record.getId())
                .taskId(record.getTaskId())
                .materialId(record.getMaterialId())
                .userId(record.getUserId())
                .taskType(record.getTaskType())
                .retryCount(record.getRetryCount())
                .failReason(record.getFailReason())
                .recordStatus(record.getRecordStatus())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
