package com.abeyoshihiro.character_manager;

import java.util.LinkedHashSet;
import java.util.Set;

final class TagParser {
    private TagParser() { }

    static Set<String> parse(String input) {
        Set<String> tags = new LinkedHashSet<>();
        if (input == null || input.isBlank()) {
            return tags;
        }
        for (String part : input.split("[,、，\\r\\n]+")) {
            String tag = part.strip();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > 30) {
                throw new IllegalArgumentException("タグは1個30文字以内で入力してください。");
            }
            tags.add(tag);
        }
        if (tags.size() > 20) {
            throw new IllegalArgumentException("タグは1キャラクターにつき20個まで登録できます。");
        }
        return tags;
    }
}
