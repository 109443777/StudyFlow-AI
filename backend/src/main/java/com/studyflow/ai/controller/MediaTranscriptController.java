package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.MediaTranscriptQueryDTO;
import com.studyflow.ai.entity.MediaTranscript;
import com.studyflow.ai.service.MediaTranscriptService;
import com.studyflow.ai.service.media.TranscriptSegment;
import com.studyflow.ai.vo.MediaTranscriptVO;
import com.studyflow.ai.vo.TranscriptSegmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Media Transcript")
@RestController
@RequestMapping("/api/media-transcripts")
@RequiredArgsConstructor
public class MediaTranscriptController {

    private final MediaTranscriptService mediaTranscriptService;

    @LoginRequired
    @Operation(summary = "Get media transcript")
    @GetMapping
    public Result<MediaTranscriptVO> getMediaTranscript(@Valid @ModelAttribute MediaTranscriptQueryDTO mediaTranscriptQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        MediaTranscript mediaTranscript = mediaTranscriptService.getByMaterialId(userId, mediaTranscriptQueryDTO);
        return Result.success(MediaTranscriptVO.builder()
                .id(mediaTranscript.getId())
                .materialId(mediaTranscript.getMaterialId())
                .mediaType(mediaTranscript.getMediaType())
                .transcriptText(mediaTranscript.getTranscriptText())
                .transcriptSegments(toSegmentVOs(mediaTranscriptService.readSegments(mediaTranscript.getTranscriptSegments())))
                .duration(mediaTranscript.getDuration())
                .transcriptStatus(mediaTranscript.getTranscriptStatus())
                .createTime(mediaTranscript.getCreateTime())
                .updateTime(mediaTranscript.getUpdateTime())
                .build());
    }

    private List<TranscriptSegmentVO> toSegmentVOs(List<TranscriptSegment> segments) {
        return segments.stream()
                .map(segment -> TranscriptSegmentVO.builder()
                        .index(segment.getIndex())
                        .startMillis(segment.getStartMillis())
                        .endMillis(segment.getEndMillis())
                        .text(segment.getText())
                        .build())
                .toList();
    }
}
