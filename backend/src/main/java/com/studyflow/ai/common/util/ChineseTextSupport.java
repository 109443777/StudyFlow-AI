package com.studyflow.ai.common.util;

import java.util.Collection;
import org.springframework.util.StringUtils;

public final class ChineseTextSupport {

    private ChineseTextSupport() {
    }

    public static boolean containsChinese(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.codePoints().anyMatch(ChineseTextSupport::isChineseCodePoint);
    }

    public static boolean isMostlyChinese(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        int chineseCount = 0;
        int letterOrDigitCount = 0;
        int otherLetterCount = 0;
        int length = text.length();
        for (int index = 0; index < length; index++) {
            char current = text.charAt(index);
            if (isChineseCodePoint(current)) {
                chineseCount++;
                continue;
            }
            if (Character.isLetterOrDigit(current)) {
                letterOrDigitCount++;
                if (current < 128) {
                    otherLetterCount++;
                }
            }
        }
        if (chineseCount == 0) {
            return false;
        }
        if (letterOrDigitCount == 0) {
            return true;
        }
        return chineseCount >= otherLetterCount;
    }

    public static boolean containsChinese(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(ChineseTextSupport::containsChinese);
    }

    private static boolean isChineseCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN;
    }
}
