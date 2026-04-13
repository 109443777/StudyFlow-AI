package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.AbortUploadDTO;
import com.studyflow.ai.dto.CompleteUploadDTO;
import com.studyflow.ai.dto.InitUploadDTO;
import com.studyflow.ai.dto.UploadChunkDTO;
import com.studyflow.ai.service.UploadSessionService;
import com.studyflow.ai.vo.ChunkUploadVO;
import com.studyflow.ai.vo.InitUploadVO;
import com.studyflow.ai.vo.MaterialVO;
import com.studyflow.ai.vo.UploadedChunksVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Multipart Upload")
@RestController
@RequestMapping("/api/material-uploads")
@RequiredArgsConstructor
public class UploadSessionController {

    private final UploadSessionService uploadSessionService;

    @LoginRequired
    @Operation(summary = "Init upload session")
    @PostMapping("/init")
    public Result<InitUploadVO> initUpload(@Valid @RequestBody InitUploadDTO initUploadDTO) {
        return Result.success(uploadSessionService.initUpload(initUploadDTO));
    }

    @LoginRequired
    @Operation(summary = "Upload multipart part")
    @PostMapping({"/part", "/chunk"})
    public Result<ChunkUploadVO> uploadPart(@Valid @ModelAttribute UploadChunkDTO uploadChunkDTO) {
        return Result.success(uploadSessionService.uploadPart(uploadChunkDTO));
    }

    @LoginRequired
    @Operation(summary = "List uploaded multipart parts")
    @GetMapping({"/{uploadId}/parts", "/{uploadId}/chunks"})
    public Result<UploadedChunksVO> listUploadedParts(@PathVariable String uploadId) {
        return Result.success(uploadSessionService.listUploadedParts(uploadId));
    }

    @LoginRequired
    @Operation(summary = "Complete upload")
    @PostMapping("/complete")
    public Result<MaterialVO> completeUpload(@Valid @RequestBody CompleteUploadDTO completeUploadDTO) {
        return Result.success(uploadSessionService.completeUpload(completeUploadDTO));
    }

    @LoginRequired
    @Operation(summary = "Abort upload")
    @PostMapping("/abort")
    public Result<Void> abortUpload(@Valid @RequestBody AbortUploadDTO abortUploadDTO) {
        uploadSessionService.abortUpload(abortUploadDTO);
        return Result.success(null);
    }
}
