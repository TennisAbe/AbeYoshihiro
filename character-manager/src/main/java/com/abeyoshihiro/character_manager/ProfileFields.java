package com.abeyoshihiro.character_manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProfileFields {
    private ProfileFields() { }
    static Map<String, Integer> limits() {
        Map<String, Integer> fields = new LinkedHashMap<>();
        fields.put("name", 255); fields.put("affiliation", 120); fields.put("basicSettings", 12000);
        fields.put("personality", 2000); fields.put("speechStyle", 2000); fields.put("firstPerson", 120);
        fields.put("feelingsTowardMikado", 4000); fields.put("likes", 2000); fields.put("dislikes", 2000); fields.put("abilities", 4000);
        return fields;
    }
    static Map<String, String> values(OriginalCharacter c) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", c.getName()); fields.put("affiliation", c.getAffiliation()); fields.put("basicSettings", c.getBasicSettings());
        fields.put("personality", c.getPersonality()); fields.put("speechStyle", c.getSpeechStyle()); fields.put("firstPerson", c.getFirstPerson());
        fields.put("feelingsTowardMikado", c.getFeelingsTowardMikado()); fields.put("likes", c.getLikes()); fields.put("dislikes", c.getDislikes()); fields.put("abilities", c.getAbilities());
        return fields;
    }
    static void copy(OriginalCharacter from, OriginalCharacter to) {
        read(values(from), to); to.setName(to.getName().strip());
        to.replaceTags(TagParser.parse(from.getTagsInput()));
    }
    static void read(Map<String, String> v, OriginalCharacter c) {
        c.setName(v.get("name")); c.setAffiliation(v.get("affiliation")); c.setBasicSettings(v.get("basicSettings"));
        c.setPersonality(v.get("personality")); c.setSpeechStyle(v.get("speechStyle")); c.setFirstPerson(v.get("firstPerson"));
        c.setFeelingsTowardMikado(v.get("feelingsTowardMikado")); c.setLikes(v.get("likes")); c.setDislikes(v.get("dislikes")); c.setAbilities(v.get("abilities"));
    }
    static Map<String, Integer> legacyLimits() {
        Map<String, Integer> fields = new LinkedHashMap<>();
        fields.put("name", 255); fields.put("personality", 2000); fields.put("memo", 8000);
        fields.put("age", 40); fields.put("birthday", 40); fields.put("height", 40); fields.put("species", 80);
        fields.put("affiliation", 120); fields.put("abilities", 4000); return fields;
    }
    static void readLegacy(Map<String, String> v, OriginalCharacter c) {
        c.setName(v.get("name")); c.setPersonality(v.get("personality")); c.setMemo(v.get("memo"));
        c.setAge(v.get("age")); c.setBirthday(v.get("birthday")); c.setHeight(v.get("height")); c.setSpecies(v.get("species"));
        c.setAffiliation(v.get("affiliation")); c.setAbilities(v.get("abilities")); migrateLegacy(c);
    }
    static boolean migrateLegacy(OriginalCharacter c) {
        if (c.getBasicSettings() != null) return false;
        List<String> parts = new ArrayList<>();
        if (c.getMemo() != null && !c.getMemo().isBlank()) parts.add(c.getMemo());
        addLegacy(parts, "年齢", c.getAge()); addLegacy(parts, "誕生日", c.getBirthday());
        addLegacy(parts, "身長", c.getHeight()); addLegacy(parts, "種族", c.getSpecies());
        c.setBasicSettings(String.join("\n\n", parts)); return true;
    }
    private static void addLegacy(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) parts.add("【" + label + "】\n" + value);
    }
    static void validate(OriginalCharacter c) {
        if (c.getName() == null || c.getName().isBlank()) throw new IllegalArgumentException("名前を入力してください。");
        values(c).forEach((key, value) -> {
            if (value != null && value.length() > limits().get(key)) throw new IllegalArgumentException(key + "の文字数が上限を超えています。");
        });
    }
}
