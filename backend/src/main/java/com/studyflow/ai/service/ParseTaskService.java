package com.studyflow.ai.service;

import com.studyflow.ai.dto.ParseTaskQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.ParseTask;
import java.util.List;

public interface ParseTaskService {

    void createAndDispatchInitialTask(Material material);

    ParseTask requestInitialParse(Long userId, Long materialId);

    void processTask(Long taskId);

    ParseTask getTaskById(Long taskId);

    ParseTask getTaskDetail(Long taskId, Long userId);

    List<ParseTask> listTasks(Long userId, ParseTaskQueryDTO parseTaskQueryDTO);
}
