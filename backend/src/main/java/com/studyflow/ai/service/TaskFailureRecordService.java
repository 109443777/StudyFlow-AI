package com.studyflow.ai.service;

import com.studyflow.ai.dto.TaskFailureQueryDTO;
import com.studyflow.ai.entity.ParseTask;
import com.studyflow.ai.entity.TaskFailureRecord;
import java.util.List;

public interface TaskFailureRecordService {

    void recordFailure(ParseTask parseTask, String failReason);

    List<TaskFailureRecord> listFailures(Long userId, TaskFailureQueryDTO taskFailureQueryDTO);

    TaskFailureRecord compensate(Long userId, Long recordId);
}
