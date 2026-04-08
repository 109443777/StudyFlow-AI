package com.studyflow.ai.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaterialTypeEnum {

    DOCUMENT(Set.of("pdf", "doc", "docx")),
    PPT(Set.of("ppt", "pptx")),
    VIDEO(Set.of("mp4")),
    AUDIO(Set.of("mp3", "wav")),
    TEXT(Set.of("txt", "md", "markdown"));

    private final Set<String> extensions;

    public static MaterialTypeEnum fromExtension(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(item -> item.extensions.contains(normalized))
                .findFirst()
                .orElse(null);
    }
}
