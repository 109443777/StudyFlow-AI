package com.studyflow.ai.service.upload;

import com.studyflow.ai.config.UploadProperties;
import com.studyflow.ai.vo.UploadedPartVO;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisUploadProgressCache implements UploadProgressCache {

    private final StringRedisTemplate stringRedisTemplate;

    private final UploadProperties uploadProperties;

    @Override
    public void initSession(String uploadId, Long partSize, Integer totalParts, String fileMd5, String status, Long materialId, Long userId, String storageUploadId) {
        String metaKey = buildMetaKey(uploadId);
        stringRedisTemplate.opsForHash().putAll(metaKey, Map.of(
                "partSize", String.valueOf(partSize),
                "totalParts", String.valueOf(totalParts),
                "fileMd5", fileMd5,
                "status", status,
                "materialId", String.valueOf(materialId),
                "userId", String.valueOf(userId),
                "storageUploadId", storageUploadId));
        expireKeys(uploadId);
    }

    @Override
    public Optional<String> getUploadedPartEtag(String uploadId, Integer partNumber) {
        Object etag = stringRedisTemplate.opsForHash().get(buildPartsKey(uploadId), String.valueOf(partNumber));
        return Optional.ofNullable(etag).map(String::valueOf);
    }

    @Override
    public Integer saveUploadedPart(String uploadId, Integer partNumber, String etag) {
        stringRedisTemplate.opsForHash().put(buildPartsKey(uploadId), String.valueOf(partNumber), etag);
        expireKeys(uploadId);
        Long size = stringRedisTemplate.opsForHash().size(buildPartsKey(uploadId));
        return size == null ? 0 : size.intValue();
    }

    @Override
    public List<UploadedPartVO> getUploadedParts(String uploadId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(buildPartsKey(uploadId));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.entrySet().stream()
                .map(entry -> UploadedPartVO.builder()
                        .partNumber(Integer.valueOf(String.valueOf(entry.getKey())))
                        .etag(String.valueOf(entry.getValue()))
                        .build())
                .sorted(Comparator.comparing(UploadedPartVO::getPartNumber))
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(String uploadId, String status) {
        stringRedisTemplate.opsForHash().put(buildMetaKey(uploadId), "status", status);
        expireKeys(uploadId);
    }

    @Override
    public void clear(String uploadId) {
        stringRedisTemplate.delete(buildMetaKey(uploadId));
        stringRedisTemplate.delete(buildPartsKey(uploadId));
    }

    private void expireKeys(String uploadId) {
        Duration ttl = Duration.ofHours(uploadProperties.getSessionExpireHours());
        stringRedisTemplate.expire(buildMetaKey(uploadId), ttl);
        stringRedisTemplate.expire(buildPartsKey(uploadId), ttl);
    }

    private String buildMetaKey(String uploadId) {
        return "studyflow:upload:session:" + uploadId;
    }

    private String buildPartsKey(String uploadId) {
        return "studyflow:upload:parts:" + uploadId;
    }
}
