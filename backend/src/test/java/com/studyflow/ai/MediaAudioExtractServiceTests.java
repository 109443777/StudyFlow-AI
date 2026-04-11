package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.service.MediaAudioExtractService;
import com.studyflow.ai.service.impl.MediaAudioExtractServiceImpl;
import com.studyflow.ai.service.media.AudioExtractionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaAudioExtractServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractAudioByInvokingConfiguredCommand() throws Exception {
        Path fakeVideo = Files.writeString(tempDir.resolve("input.mp4"), "video-data");
        Path fakeFfmpeg = createFakeFfmpeg("copy");

        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(fakeFfmpeg.toString());
        properties.setTempDir(tempDir.toString());
        properties.setFfmpegTimeoutSeconds(5);

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);
        AudioExtractionResult result = service.extractToWav(fakeVideo, 1001L);

        assertTrue(Files.exists(result.getAudioFilePath()));
        assertEquals("material-1001-extract.wav", result.getAudioFilePath().getFileName().toString());
        assertEquals("video-data", Files.readString(result.getAudioFilePath()));
    }

    @Test
    void shouldThrowBusinessExceptionWhenFfmpegExecutableIsMissing() throws Exception {
        Path fakeVideo = Files.writeString(tempDir.resolve("broken.mp4"), "video-data");
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(tempDir.resolve("missing-ffmpeg.exe").toString());
        properties.setTempDir(tempDir.toString());
        properties.setFfmpegTimeoutSeconds(1);

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);

        assertThrows(BusinessException.class, () -> service.extractToWav(fakeVideo, 1002L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenFfmpegReturnsNonZeroExitCode() throws Exception {
        Path fakeVideo = Files.writeString(tempDir.resolve("failed.mp4"), "video-data");
        Path fakeFfmpeg = createFakeFfmpeg("fail");
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(fakeFfmpeg.toString());
        properties.setTempDir(tempDir.toString());
        properties.setFfmpegTimeoutSeconds(5);

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);

        assertThrows(BusinessException.class, () -> service.extractToWav(fakeVideo, 1003L));
    }

    private Path createFakeFfmpeg(String mode) throws Exception {
        boolean windows = OS.WINDOWS.isCurrentOs();
        Path script = tempDir.resolve(windows ? "fake-ffmpeg.cmd" : "fake-ffmpeg.sh");
        String content;
        if ("copy".equals(mode)) {
            content = windows ? "@echo off\r\ncopy %3 %7 >nul\r\n" : "#!/bin/sh\ncp \"$3\" \"$7\"\n";
        } else {
            content = windows ? "@echo off\r\nexit /b 2\r\n" : "#!/bin/sh\nexit 2\n";
        }
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
