package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.TranscriptionProperties;
import com.studyflow.ai.service.MediaAudioExtractService;
import com.studyflow.ai.service.impl.MediaAudioExtractServiceImpl;
import com.studyflow.ai.service.media.AudioExtractionResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
        Path argsFile = tempDir.resolve("ffmpeg-args.txt");

        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setFfmpegPath(fakeFfmpeg.toString());
        properties.setTempDir(tempDir.toString());
        properties.setFfmpegTimeoutSeconds(5);

        MediaAudioExtractService service = new MediaAudioExtractServiceImpl(properties);
        AudioExtractionResult result = service.extractToWav(fakeVideo, 1001L);

        assertTrue(Files.exists(result.getAudioFilePath()));
        assertEquals("material-1001-extract.mp3", result.getAudioFilePath().getFileName().toString());
        assertEquals("extracted-audio", Files.readString(result.getAudioFilePath()));
        List<String> args = Arrays.stream(Files.readString(argsFile, StandardCharsets.ISO_8859_1).split("\\R"))
                .filter(value -> !value.isBlank())
                .toList();
        assertTrue(args.contains("-vn"));
        assertTrue(args.contains("-ac"));
        assertTrue(args.contains("-ar"));
        assertTrue(args.contains("16000"));
        assertTrue(args.contains("-codec:a"));
        assertTrue(args.contains("libmp3lame"));
        assertTrue(args.contains("-b:a"));
        assertTrue(args.contains("64k"));
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
        Path argsFile = tempDir.resolve("ffmpeg-args.txt");
        String content;
        if ("copy".equals(mode)) {
            content = windows
                    ? """
                    @echo off
                    setlocal enabledelayedexpansion
                    set "argsFile=%s"
                    if exist "%%argsFile%%" del "%%argsFile%%"
                    :loop
                    if "%%~1"=="" goto done
                    echo %%~1>>"%%argsFile%%"
                    set "out=%%~1"
                    shift
                    goto loop
                    :done
                    <nul set /p="extracted-audio" > "%%out%%"
                    exit /b 0
                    """.formatted(argsFile)
                    : """
                    #!/bin/sh
                    args_file='%s'
                    : > "$args_file"
                    out=""
                    for arg in "$@"; do
                      printf '%%s\\n' "$arg" >> "$args_file"
                      out="$arg"
                    done
                    printf 'extracted-audio' > "$out"
                    """.formatted(argsFile);
        } else {
            content = windows ? "@echo off\r\nexit /b 2\r\n" : "#!/bin/sh\nexit 2\n";
        }
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
