package com.studyflow.ai.service.upload;

import com.studyflow.ai.vo.UploadedPartVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryUploadProgressCache implements UploadProgressCache {

    private final Map<String, Map<Integer, String>> uploadedPartsMap = new ConcurrentHashMap<>();

    @Override
    public void initSession(String uploadId, Long partSize, Integer totalParts, String fileMd5, String status, Long materialId, Long userId, String storageUploadId) {
        uploadedPartsMap.computeIfAbsent(uploadId, key -> new ConcurrentHashMap<>());
    }

    @Override
    public Optional<String> getUploadedPartEtag(String uploadId, Integer partNumber) {
        return Optional.ofNullable(uploadedPartsMap.getOrDefault(uploadId, Map.of()).get(partNumber));
    }

    @Override
    public Integer saveUploadedPart(String uploadId, Integer partNumber, String etag) {
        Map<Integer, String> uploadedParts = uploadedPartsMap.computeIfAbsent(uploadId, key -> new ConcurrentHashMap<>());
        uploadedParts.put(partNumber, etag);
        return uploadedParts.size();
    }

    @Override
    public List<UploadedPartVO> getUploadedParts(String uploadId) {
        return uploadedPartsMap.getOrDefault(uploadId, Map.of()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> UploadedPartVO.builder()
                        .partNumber(entry.getKey())
                        .etag(entry.getValue())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void updateStatus(String uploadId, String status) {
    }

    @Override
    public void clear(String uploadId) {
        uploadedPartsMap.remove(uploadId);
    }
}
