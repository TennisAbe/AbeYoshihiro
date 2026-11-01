package com.abeyoshihiro.character_manager;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TagParserTests {
    @Test
    void normalizesSeparatorsSpacesAndDuplicates() {
        assertEquals(Set.of("魔法使い", "主人公", "森", "Alice"),
                TagParser.parse(" 魔法使い,主人公、魔法使い，森\r\nAlice,, "));
        assertTrue(TagParser.parse(null).isEmpty());
        assertTrue(TagParser.parse(" ,、， \n").isEmpty());
        assertEquals(Set.of("ranger", "忍者"), TagParser.parse("ranger,忍者"));
    }

    @Test
    void checksLengthAndCountAfterDeduplication() {
        assertEquals(1, TagParser.parse("あ".repeat(30)).size());
        assertThrows(IllegalArgumentException.class, () -> TagParser.parse("あ".repeat(31)));
        String twenty = IntStream.range(0, 20).mapToObj(i -> "タグ" + i).collect(Collectors.joining(","));
        assertEquals(20, TagParser.parse(twenty + ",タグ0").size());
        assertThrows(IllegalArgumentException.class, () -> TagParser.parse(twenty + ",追加"));
    }
}
