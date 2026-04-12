package com.studyflow.ai.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TextChunkSupport {

    private static final List<Character> BREAK_CHARACTERS = List.of(
            '\n', '.', '?', '!', ';', ',',
            '。', '？', '！', '；', '，');

    private static final Pattern CHAPTER_HEADING_PATTERN = Pattern.compile(
            "^(#{1,6}\\s+.+|第[一二三四五六七八九十百千万0-9]+[章节讲课部分篇].*|[0-9]+(\\.[0-9]+)*[.、．)]\\s+.+)$");

    private TextChunkSupport() {
    }

    public static List<String> split(String text, int chunkSize, int overlap) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        int safeChunkSize = Math.max(200, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunkSize / 2));
        List<String> chunks = new ArrayList<>();
        for (String section : splitSections(normalized)) {
            appendSectionChunks(section, safeChunkSize, safeOverlap, chunks);
        }
        return chunks;
    }

    public static int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length > 1) {
            return tokens.length;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0D));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static List<String> splitSections(String text) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (isChapterHeading(line) && !current.isEmpty()) {
                addIfNotBlank(sections, current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        addIfNotBlank(sections, current.toString());
        return sections;
    }

    private static boolean isChapterHeading(String line) {
        String trimmed = line == null ? "" : line.trim();
        return !trimmed.isBlank() && CHAPTER_HEADING_PATTERN.matcher(trimmed).matches();
    }

    private static void appendSectionChunks(
            String section,
            int chunkSize,
            int overlap,
            List<String> chunks) {
        if (section.length() <= chunkSize) {
            addIfNotBlank(chunks, section);
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : splitParagraphs(section)) {
            if (paragraph.length() > chunkSize) {
                flushCurrent(current, chunks);
                chunks.addAll(splitOversizedBlock(paragraph, chunkSize, overlap));
                continue;
            }
            if (current.isEmpty()) {
                current.append(paragraph);
                continue;
            }
            if (current.length() + paragraph.length() + 2 <= chunkSize) {
                current.append("\n\n").append(paragraph);
            } else {
                flushCurrent(current, chunks);
                current.append(paragraph);
            }
        }
        flushCurrent(current, chunks);
    }

    private static List<String> splitParagraphs(String section) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : section.split("\\n\\s*\\n")) {
            addIfNotBlank(paragraphs, paragraph);
        }
        return paragraphs;
    }

    private static List<String> splitOversizedBlock(String block, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(block.length(), start + chunkSize);
            int actualEnd = findBreakPoint(block, start, end);
            addIfNotBlank(chunks, block.substring(start, actualEnd));
            if (actualEnd >= block.length()) {
                break;
            }
            start = Math.max(actualEnd - overlap, start + 1);
        }
        return chunks;
    }

    private static int findBreakPoint(String text, int start, int end) {
        int minBreakpoint = start + Math.max(1, (int) ((end - start) * 0.6D));
        for (int i = end - 1; i >= minBreakpoint; i--) {
            if (BREAK_CHARACTERS.contains(text.charAt(i))) {
                return i + 1;
            }
        }
        return end;
    }

    private static void flushCurrent(StringBuilder current, List<String> chunks) {
        addIfNotBlank(chunks, current.toString());
        current.setLength(0);
    }

    private static void addIfNotBlank(List<String> values, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.isBlank()) {
            values.add(trimmed);
        }
    }
}
