package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyflow.ai.common.util.TextChunkSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkSupportTests {

    @Test
    void shouldPreferChineseSentenceBoundaryWhenSplitting() {
        String firstSentence = "数据结构".repeat(35) + "。";
        String secondSentence = "算法分析".repeat(35) + "。";

        List<String> chunks = TextChunkSupport.split(firstSentence + secondSentence, 200, 40);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0)).endsWith("。");
    }

    @Test
    void shouldKeepShortParagraphsUnderSameChapterTogether() {
        String text = """
                第一章 绪论
                学习目标：理解课程背景。

                本章重点：掌握基础概念。

                第二章 图论
                学习目标：理解最短路径。

                本章重点：掌握 Dijkstra 算法。
                """;

        List<String> chunks = TextChunkSupport.split(text, 200, 40);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).startsWith("第一章 绪论");
        assertThat(chunks.get(0)).doesNotContain("第二章 图论");
        assertThat(chunks.get(1)).startsWith("第二章 图论");
    }

    @Test
    void shouldApplySlidingWindowOnlyForOversizedBlock() {
        String longParagraph = "操作系统调度策略".repeat(80);

        List<String> chunks = TextChunkSupport.split(longParagraph, 200, 50);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(200));
        assertThat(chunks.get(0).substring(chunks.get(0).length() - 50))
                .isEqualTo(chunks.get(1).substring(0, 50));
    }
}
