package com.studyflow.ai.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TextCleanupSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MULTI_BLANK_LINE_PATTERN = Pattern.compile("(\\r?\\n\\s*){3,}");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f]{2,}");
    private static final Pattern MARKDOWN_PREFIX_PATTERN = Pattern.compile("^(#{1,6}\\s*|[-*+]\\s+|\\d+\\.\\s+)");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^(chapter\\s+\\d+|part\\s+\\d+|section\\s+\\d+|第[一二三四五六七八九十百零0-9]+[章节篇部分]).*",
            Pattern.CASE_INSENSITIVE);

    private TextCleanupSupport() {
    }

    public static String cleanText(String rawText) {
        if (rawText == null) {
            return "";
        }
        String normalized = rawText
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] lines = normalized.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String normalizedLine = MULTI_SPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (normalizedLine.isEmpty()) {
                cleaned.append('\n');
                continue;
            }
            cleaned.append(stripMarkdownMarker(normalizedLine)).append('\n');
        }
        return MULTI_BLANK_LINE_PATTERN.matcher(cleaned.toString())
                .replaceAll("\n\n")
                .trim();
    }

    public static List<String> extractChapterInfo(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String[] lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        Set<String> chapters = new LinkedHashSet<>();
        for (String line : lines) {
            String candidate = stripMarkdownMarker(MULTI_SPACE_PATTERN.matcher(line).replaceAll(" ").trim());
            if (candidate.isEmpty()) {
                continue;
            }
            if (isChapterHeading(candidate)) {
                chapters.add(candidate);
            }
            if (chapters.size() >= 20) {
                break;
            }
        }
        return new ArrayList<>(chapters);
    }

    public static String writeChapterInfo(List<String> chapterInfo) {
        try {
            return OBJECT_MAPPER.writeValueAsString(chapterInfo == null ? List.of() : chapterInfo);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize chapter info", exception);
        }
    }

    public static List<String> readChapterInfo(String chapterInfo) {
        if (chapterInfo == null || chapterInfo.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(chapterInfo, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("Failed to deserialize chapter info, raw={}", chapterInfo, exception);
            return List.of();
        }
    }

    private static boolean isChapterHeading(String candidate) {
        if (candidate.length() > 80) {
            return false;
        }
        if (candidate.startsWith("#")) {
            return true;
        }
        if (CHAPTER_PATTERN.matcher(candidate.toLowerCase(Locale.ROOT)).matches()) {
            return true;
        }
        return !candidate.endsWith("。")
                && !candidate.endsWith(".")
                && !candidate.endsWith("；")
                && !candidate.endsWith(";")
                && !candidate.endsWith("：")
                && !candidate.endsWith(":")
                && candidate.split("\\s+").length <= 10;
    }

    private static String stripMarkdownMarker(String line) {
        return MARKDOWN_PREFIX_PATTERN.matcher(line).replaceFirst("").trim();
    }
}
