package com.studyflow.ai.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MinioConfig {

    @Bean
    @ConditionalOnExpression(
            "T(org.springframework.util.StringUtils).hasText('${studyflow.minio.endpoint:}') "
                    + "and T(org.springframework.util.StringUtils).hasText('${studyflow.minio.access-key:}') "
                    + "and T(org.springframework.util.StringUtils).hasText('${studyflow.minio.secret-key:}')")
    public MinioClient minioClient(MinioProperties minioProperties) {
        if (!StringUtils.hasText(minioProperties.getAccessKey()) || !StringUtils.hasText(minioProperties.getSecretKey())) {
            throw new IllegalStateException("MinIO accessKey and secretKey must be configured when endpoint is enabled");
        }
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
