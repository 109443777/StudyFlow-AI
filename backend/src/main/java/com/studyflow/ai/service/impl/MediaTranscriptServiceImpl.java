package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.common.util.TextCleanupSupport;
import com.studyflow.ai.dto.MediaTranscriptQueryDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.MediaTranscript;
import com.studyflow.ai.enums.MaterialTypeEnum;
import com.studyflow.ai.enums.MediaTypeEnum;
import com.studyflow.ai.enums.ParseTaskTypeEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.enums.TranscriptStatusEnum;
import com.studyflow.ai.gateway.MediaTranscriptionGateway;
import com.studyflow.ai.gateway.MediaTranscriptionRequest;
import com.studyflow.ai.gateway.StorageGateway;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.MediaTranscriptMapper;
import com.studyflow.ai.service.MaterialContentService;
import com.studyflow.ai.service.MediaTranscriptService;
import com.studyflow.ai.service.media.MediaTranscriptionResult;
import com.studyflow.ai.service.media.TranscriptSegment;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaTranscriptServiceImpl implements MediaTranscriptService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MediaTranscriptMapper mediaTranscriptMapper;

    private final MaterialMapper materialMapper;

    private final StorageGateway storageGateway;

    private final MediaTranscriptionGateway mediaTranscriptionGateway;

    private final MaterialContentService materialContentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaTranscript transcribeAndSave(Material material, ParseTaskTypeEnum taskType) {
        MediaTypeEnum mediaType = resolveMediaType(material, taskType);
        MediaTranscript mediaTranscript = getOrCreateTranscript(material.getId(), mediaType);
        markTranscriptRunning(mediaTranscript);
        try (InputStream inputStream = storageGateway.download(material.getObjectKey())) {
            byte[] content = inputStream.readAllBytes();
            MediaTranscriptionResult result = mediaTranscriptionGateway.transcribe(MediaTranscriptionRequest.builder()
                    .materialId(material.getId())
                    .fileName(material.getFileName())
                    .fileType(material.getFileType())
                    .mediaType(mediaType)
                    .content(content)
                    .build());
            String cleanedText = TextCleanupSupport.cleanText(result.getTranscriptText());
            List<String> chapterInfo = TextCleanupSupport.extractChapterInfo(result.getTranscriptText());
            materialContentService.saveOrUpdatePlainText(
                    material.getId(),
                    result.getTranscriptText(),
                    cleanedText,
                    chapterInfo);

            mediaTranscript.setMediaType(mediaType.name());
            mediaTranscript.setTranscriptText(result.getTranscriptText());
            mediaTranscript.setTranscriptSegments(writeSegments(result.getTranscriptSegments()));
            mediaTranscript.setDuration(result.getDuration());
            mediaTranscript.setTranscriptStatus(TranscriptStatusEnum.SUCCESS.name());
            mediaTranscriptMapper.updateById(mediaTranscript);
            return mediaTranscriptMapper.selectById(mediaTranscript.getId());
        } catch (IOException exception) {
            markTranscriptFailed(mediaTranscript);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to download media from storage");
        } catch (RuntimeException exception) {
            markTranscriptFailed(mediaTranscript);
            throw exception;
        }
    }

    @Override
    public MediaTranscript getByMaterialId(Long userId, MediaTranscriptQueryDTO mediaTranscriptQueryDTO) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, mediaTranscriptQueryDTO.getMaterialId())
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        MediaTranscript mediaTranscript = mediaTranscriptMapper.selectOne(new LambdaQueryWrapper<MediaTranscript>()
                .eq(MediaTranscript::getMaterialId, material.getId())
                .last("limit 1"));
        if (mediaTranscript == null) {
            throw new BusinessException(ResultCodeEnum.MEDIA_TRANSCRIPT_NOT_FOUND);
        }
        return mediaTranscript;
    }

    @Override
    public List<TranscriptSegment> readSegments(String transcriptSegments) {
        if (transcriptSegments == null || transcriptSegments.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(transcriptSegments, new TypeReference<List<TranscriptSegment>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to read transcript segments");
        }
    }

    private MediaTypeEnum resolveMediaType(Material material, ParseTaskTypeEnum taskType) {
        if (taskType == ParseTaskTypeEnum.AUDIO_TRANSCRIBE) {
            return MediaTypeEnum.AUDIO;
        }
        if (taskType == ParseTaskTypeEnum.VIDEO_TRANSCRIBE) {
            return MediaTypeEnum.VIDEO;
        }
        MaterialTypeEnum materialType = MaterialTypeEnum.valueOf(material.getMaterialType());
        return materialType == MaterialTypeEnum.AUDIO ? MediaTypeEnum.AUDIO : MediaTypeEnum.VIDEO;
    }

    private MediaTranscript getOrCreateTranscript(Long materialId, MediaTypeEnum mediaType) {
        MediaTranscript existingTranscript = mediaTranscriptMapper.selectOne(new LambdaQueryWrapper<MediaTranscript>()
                .eq(MediaTranscript::getMaterialId, materialId)
                .last("limit 1"));
        if (existingTranscript != null) {
            return existingTranscript;
        }
        MediaTranscript mediaTranscript = new MediaTranscript();
        mediaTranscript.setMaterialId(materialId);
        mediaTranscript.setMediaType(mediaType.name());
        mediaTranscript.setTranscriptText("");
        mediaTranscript.setTranscriptSegments("[]");
        mediaTranscript.setDuration(0L);
        mediaTranscript.setTranscriptStatus(TranscriptStatusEnum.INIT.name());
        mediaTranscriptMapper.insert(mediaTranscript);
        return mediaTranscript;
    }

    private void markTranscriptRunning(MediaTranscript mediaTranscript) {
        mediaTranscript.setTranscriptStatus(TranscriptStatusEnum.RUNNING.name());
        mediaTranscriptMapper.updateById(mediaTranscript);
    }

    private void markTranscriptFailed(MediaTranscript mediaTranscript) {
        mediaTranscript.setTranscriptStatus(TranscriptStatusEnum.FAILED.name());
        mediaTranscriptMapper.updateById(mediaTranscript);
    }

    private String writeSegments(List<TranscriptSegment> segments) {
        try {
            return OBJECT_MAPPER.writeValueAsString(segments == null ? List.of() : segments);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to save transcript segments");
        }
    }
}
