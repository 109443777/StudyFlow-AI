package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.ParseTaskQueryDTO;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.service.ParseTaskService;
import com.studyflow.ai.vo.ParseTaskVO;
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

@Tag(name = "Parse Task")
@RestController
@RequestMapping("/api/parse-tasks")
@RequiredArgsConstructor
public class ParseTaskController {

    private final ParseTaskService parseTaskService;

    @LoginRequired
    @Operation(summary = "Get parse task detail")
    @GetMapping("/{taskId}")
    public Result<ParseTaskVO> getTaskDetail(@PathVariable Long taskId) {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(toParseTaskVO(parseTaskService.getTaskDetail(taskId, userId)));
    }

    @LoginRequired
    @Operation(summary = "List parse tasks")
    @GetMapping
    public Result<List<ParseTaskVO>> listTasks(@ModelAttribute ParseTaskQueryDTO parseTaskQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(parseTaskService.listTasks(userId, parseTaskQueryDTO)
                .stream()
                .map(this::toParseTaskVO)
                .toList());
    }

    @LoginRequired
    @Operation(summary = "Dispatch initial parse task for material")
    @PostMapping("/materials/{materialId}/dispatch")
    public Result<ParseTaskVO> dispatchMaterialParse(@PathVariable Long materialId) {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(toParseTaskVO(parseTaskService.requestInitialParse(userId, materialId)));
    }

    private ParseTaskVO toParseTaskVO(ParseTask parseTask) {
        return ParseTaskVO.builder()
                .id(parseTask.getId())
                .materialId(parseTask.getMaterialId())
                .userId(parseTask.getUserId())
                .taskType(parseTask.getTaskType())
                .status(parseTask.getStatus())
                .retryCount(parseTask.getRetryCount())
                .failReason(parseTask.getFailReason())
                .startTime(parseTask.getStartTime())
                .endTime(parseTask.getEndTime())
                .createTime(parseTask.getCreateTime())
                .updateTime(parseTask.getUpdateTime())
                .build();
    }
}
