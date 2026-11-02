package com.abeyoshihiro.character_manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProfileFields {
    private ProfileFields() { }
    static Map<String, Integer> limits() {
        Map<String, Integer> fields = new LinkedHashMap<>();
        fields.put("gender", 8); fields.put("heightCm", 8); fields.put("bustCm", 8);
        fields.put("name", 255); fields.put("affiliation", 120); fields.put("basicSettings", 12000);
        fields.put("personality", 2000); fields.put("speechStyle", 2000); fields.put("firstPerson", 120);
        fields.put("feelingsTowardMikado", 4000); fields.put("likes", 2000); fields.put("dislikes", 2000); fields.put("abilities", 4000);
        return fields;
    }
    static Map<String, String> values(OriginalCharacter c) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("gender", c.getGender()); fields.put("heightCm", c.getHeightCm()); fields.put("bustCm", c.getBustCm());
        fields.put("name", c.getName()); fields.put("affiliation", c.getAffiliation()); fields.put("basicSettings", c.getBasicSettings());
        fields.put("personality", c.getPersonality()); fields.put("speechStyle", c.getSpeechStyle()); fields.put("firstPerson", c.getFirstPerson());
        fields.put("feelingsTowardMikado", c.getFeelingsTowardMikado()); fields.put("likes", c.getLikes()); fields.put("dislikes", c.getDislikes()); fields.put("abilities", c.getAbilities());
        return fields;
    }
    static void copy(OriginalCharacter from, OriginalCharacter to) {
        read(values(from), to); to.setName(to.getName().strip());
        to.setWorld(from.getWorld());
        if (to.getAffiliation() != null) to.setAffiliation(to.getAffiliation().strip());
        to.replaceTags(TagParser.parse(from.getTagsInput()));
    }
    static void read(Map<String, String> v, OriginalCharacter c) {
        c.setGender(v.get("gender")); c.setHeightCm(v.get("heightCm")); c.setBustCm("女".equals(c.getGender()) ? v.get("bustCm") : null);
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
    static Map<String, String> measurementErrors(OriginalCharacter c) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (c.getGender() != null && !c.getGender().isEmpty() && !java.util.Set.of("男", "女", "その他").contains(c.getGender()))
            errors.put("gender", "性別は男・女・その他から選択してください。");
        for (var entry : Map.of("heightCm", c.getHeightCm() == null ? "" : c.getHeightCm(), "bustCm", c.getBustCm() == null ? "" : c.getBustCm()).entrySet()) {
            String value = entry.getValue();
            if (!value.isEmpty() && (!value.matches("[0-9]{1,5}(\\.[0-9]{1,2})?") || new java.math.BigDecimal(value).signum() <= 0 || new java.math.BigDecimal(value).compareTo(new java.math.BigDecimal("10000")) > 0))
                errors.put(entry.getKey(), "cmは0より大きく10000以下、小数第2位までの数値で入力してください。");
        }
        return errors;
    }
    static void validate(OriginalCharacter c) {
        if (!measurementErrors(c).isEmpty()) throw new IllegalArgumentException(measurementErrors(c).values().iterator().next());
        if (c.getName() == null || c.getName().isBlank()) throw new IllegalArgumentException("名前を入力してください。");
        values(c).forEach((key, value) -> {
            if (value != null && value.length() > limits().get(key)) throw new IllegalArgumentException(key + "の文字数が上限を超えています。");
        });
    }
}
