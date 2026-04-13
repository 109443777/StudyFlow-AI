package com.studyflow.ai.service.upload;

import com.studyflow.ai.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultipartUploadCleanupScheduler {

    private final UploadSessionService uploadSessionService;

    @Scheduled(
            fixedDelayString = "${studyflow.upload.cleanup-interval-millis:300000}",
            initialDelayString = "${studyflow.upload.cleanup-interval-millis:300000}")
    public void cleanupExpiredUploads() {
        log.debug("Running multipart upload expiration cleanup");
        uploadSessionService.abortExpiredUploads();
    }
}
