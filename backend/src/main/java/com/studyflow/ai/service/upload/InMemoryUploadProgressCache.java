package com.studyflow.ai.service.upload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryUploadProgressCache implements UploadProgressCache {

    private final Map<String, Set<Integer>> uploadedChunksMap = new ConcurrentHashMap<>();

    @Override
    public void initSession(String uploadId, Integer totalChunks, String fileMd5, String status, Long materialId, Long userId) {
        uploadedChunksMap.computeIfAbsent(uploadId, key -> new ConcurrentSkipListSet<>());
    }

    @Override
    public boolean isChunkUploaded(String uploadId, Integer chunkIndex) {
        return uploadedChunksMap.getOrDefault(uploadId, Set.of()).contains(chunkIndex);
    }

    @Override
    public Integer markChunkUploaded(String uploadId, Integer chunkIndex) {
        Set<Integer> chunks = uploadedChunksMap.computeIfAbsent(uploadId, key -> new ConcurrentSkipListSet<>());
        chunks.add(chunkIndex);
        return chunks.size();
    }

    @Override
    public List<Integer> getUploadedChunks(String uploadId) {
        return uploadedChunksMap.getOrDefault(uploadId, Set.of())
                .stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void updateStatus(String uploadId, String status) {
    }
}
