package com.studyflow.ai.service.upload;

import com.studyflow.ai.config.UploadProperties;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public void initSession(String uploadId, Integer totalChunks, String fileMd5, String status, Long materialId, Long userId) {
        String metaKey = buildMetaKey(uploadId);
        stringRedisTemplate.opsForHash().putAll(metaKey, Map.of(
                "totalChunks", String.valueOf(totalChunks),
                "fileMd5", fileMd5,
                "status", status,
                "materialId", String.valueOf(materialId),
                "userId", String.valueOf(userId)));
        expireKeys(uploadId);
    }

    @Override
    public boolean isChunkUploaded(String uploadId, Integer chunkIndex) {
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(buildChunksKey(uploadId), String.valueOf(chunkIndex));
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public Integer markChunkUploaded(String uploadId, Integer chunkIndex) {
        stringRedisTemplate.opsForSet().add(buildChunksKey(uploadId), String.valueOf(chunkIndex));
        expireKeys(uploadId);
        Long size = stringRedisTemplate.opsForSet().size(buildChunksKey(uploadId));
        return size == null ? 0 : size.intValue();
    }

    @Override
    public List<Integer> getUploadedChunks(String uploadId) {
        Set<String> members = stringRedisTemplate.opsForSet().members(buildChunksKey(uploadId));
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(Integer::valueOf)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(String uploadId, String status) {
        stringRedisTemplate.opsForHash().put(buildMetaKey(uploadId), "status", status);
        expireKeys(uploadId);
    }

    private void expireKeys(String uploadId) {
        Duration ttl = Duration.ofHours(uploadProperties.getSessionExpireHours());
        stringRedisTemplate.expire(buildMetaKey(uploadId), ttl);
        stringRedisTemplate.expire(buildChunksKey(uploadId), ttl);
    }

    private String buildMetaKey(String uploadId) {
        return "studyflow:upload:session:" + uploadId;
    }

    private String buildChunksKey(String uploadId) {
        return "studyflow:upload:chunks:" + uploadId;
    }
}
