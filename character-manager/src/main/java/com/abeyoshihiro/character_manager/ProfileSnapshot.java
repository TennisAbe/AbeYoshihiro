package com.abeyoshihiro.character_manager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class ProfileSnapshot {
    private ProfileSnapshot() { }
    static Map<String, String> of(OriginalCharacter character) {
        Map<String, String> result = new LinkedHashMap<>();
        ProfileFields.values(character).forEach((key, value) -> result.put(key, value == null ? "" : value));
        result.put("world", character.getWorld().name());
        result.put("tagsInput", String.join(", ", character.getTags()));
        return result;
    }
    static Map<String, String> labels() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("gender", "性別"); result.put("heightCm", "身長（cm）"); result.put("bustCm", "胸囲（cm）");
        result.put("name", "名前"); result.put("world", "世界"); result.put("affiliation", "所属");
        result.put("basicSettings", "基本設定"); result.put("personality", "性格"); result.put("speechStyle", "口調");
        result.put("firstPerson", "一人称"); result.put("feelingsTowardMikado", "ミカドへの思い");
        result.put("likes", "好き"); result.put("dislikes", "嫌い"); result.put("abilities", "能力"); result.put("tagsInput", "タグ");
        return result;
    }
    static Map<String, String> compatible(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>(values);
        for (String field : List.of("gender", "heightCm", "bustCm")) { if (!result.containsKey(field)) result.put(field, ""); }
        return result;
    }
    static void validate(Map<String, String> values) {
        values = compatible(values);
        if (!values.keySet().equals(labels().keySet()) || values.values().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("履歴の項目が不足または不正です。");
        OriginalCharacter character = new OriginalCharacter(); ProfileFields.read(values, character); ProfileFields.validate(character);
        CharacterWorld.parse(values.get("world"));
        if (values.get("tagsInput").length() > 640) throw new IllegalArgumentException("履歴のタグが長すぎます。");
        TagParser.parse(values.get("tagsInput"));
    }
    static void apply(Map<String, String> values, OriginalCharacter target) {
        validate(values); ProfileFields.read(values, target);
        target.setWorld(CharacterWorld.parse(values.get("world")));
        target.replaceTags(TagParser.parse(values.get("tagsInput"))); target.setTagsInput(null);
    }
    static String fingerprint(OriginalCharacter character) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            of(character).forEach((key, value) -> {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(key.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
