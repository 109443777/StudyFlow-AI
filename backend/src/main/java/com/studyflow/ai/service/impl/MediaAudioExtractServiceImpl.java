package com.studyflow.ai.service.impl;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.service.MediaAudioExtractService;
import com.studyflow.ai.service.media.AudioExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAudioExtractServiceImpl implements MediaAudioExtractService {

    private final TranscriptionProperties transcriptionProperties;

    @Override
    public AudioExtractionResult extractToWav(Path videoPath, Long materialId) {
        if (!Files.exists(videoPath)) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "video file does not exist for audio extraction");
        }
        Path audioPath = ensureTempDir().resolve("material-" + materialId + "-extract.wav");
        List<String> command = List.of(
                transcriptionProperties.getFfmpegPath(),
                "-y",
                "-i",
                videoPath.toString(),
                "-vn",
                "-acodec",
                "pcm_s16le",
                audioPath.toString());
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(transcriptionProperties.getFfmpegTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                destroyProcess(process);
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction timed out");
            }
            if (process.exitValue() != 0 || !Files.exists(audioPath)) {
                throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction failed");
            }
            return AudioExtractionResult.builder()
                    .audioFilePath(audioPath)
                    .build();
        } catch (IOException exception) {
            log.warn("FFmpeg audio extraction failed to start, materialId={}, videoPath={}", materialId, videoPath,
                    exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction failed");
        } catch (InterruptedException exception) {
            if (process != null) {
                destroyProcessQuietly(process);
            }
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "ffmpeg audio extraction interrupted");
        }
    }

    private void destroyProcess(Process process) throws InterruptedException {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    private void destroyProcessQuietly(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private Path ensureTempDir() {
        try {
            Path tempDir = Path.of(transcriptionProperties.getTempDir());
            Files.createDirectories(tempDir);
            return tempDir;
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to create media temp directory");
        }
    }
}
