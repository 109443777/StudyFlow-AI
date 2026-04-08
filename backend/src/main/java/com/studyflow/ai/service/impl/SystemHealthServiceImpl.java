package com.studyflow.ai.service.impl;

import com.studyflow.ai.service.SystemHealthService;
import com.studyflow.ai.vo.HealthCheckVO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthServiceImpl implements SystemHealthService {

    @Override
    public HealthCheckVO healthCheck() {
        return HealthCheckVO.builder()
                .application("studyflow-ai-backend")
                .status("UP")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
