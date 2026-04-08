package com.studyflow.ai.common.util;

import java.util.ArrayList;
import java.util.List;

public final class TextChunkSupport {

    private static final List<Character> BREAK_CHARACTERS = List.of('\n', '.', '?', '!', ';', ',', '。', '？', '！', '；', '，');

    private TextChunkSupport() {
    }

    public static List<String> split(String text, int chunkSize, int overlap) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int safeChunkSize = Math.max(200, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunkSize / 2));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + safeChunkSize);
            int actualEnd = findBreakPoint(normalized, start, end);
            String chunk = normalized.substring(start, actualEnd).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (actualEnd >= normalized.length()) {
                break;
            }
            start = Math.max(actualEnd - safeOverlap, start + 1);
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

    private static int findBreakPoint(String text, int start, int end) {
        int minBreakpoint = start + Math.max(1, (int) ((end - start) * 0.6D));
        for (int i = end - 1; i >= minBreakpoint; i--) {
            if (BREAK_CHARACTERS.contains(text.charAt(i))) {
                return i + 1;
            }
        }
        return end;
    }
}
