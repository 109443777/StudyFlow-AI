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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaAudioExtractServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractAudioByInvokingConfiguredCommand() throws Exception {
        Path fakeVideo = Files.writeString(tempDir.resolve("input.mp4"), "video-data");
        Path fakeFfmpeg = Files.writeString(tempDir.resolve("fake-ffmpeg.cmd"),
                "@echo off\r\ncopy %3 %7 >nul\r\n");

        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(fakeFfmpeg.toString());
        properties.setTempDir(tempDir.toString());

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

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);

        assertThrows(BusinessException.class, () -> service.extractToWav(fakeVideo, 1002L));
    }
}
