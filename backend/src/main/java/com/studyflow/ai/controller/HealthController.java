package com.studyflow.ai.controller;

import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.service.SystemHealthService;
import com.studyflow.ai.vo.HealthCheckVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final SystemHealthService systemHealthService;

    @Operation(summary = "健康检查")
    @GetMapping
    public Result<HealthCheckVO> health() {
        return Result.success(systemHealthService.healthCheck());
    }
}
