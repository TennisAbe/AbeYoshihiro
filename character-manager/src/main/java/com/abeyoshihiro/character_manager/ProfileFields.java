package com.abeyoshihiro.character_manager;
import java.util.LinkedHashMap;
import java.util.Map;
final class ProfileFields {
    private ProfileFields() { }
    static Map<String, Integer> limits() {
        Map<String, Integer> fields = new LinkedHashMap<>();
        fields.put("name", 255); fields.put("personality", 2000); fields.put("memo", 8000);
        fields.put("age", 40); fields.put("birthday", 40); fields.put("height", 40);
        fields.put("species", 80); fields.put("affiliation", 120); fields.put("abilities", 4000);
        return fields;
    }
    static Map<String, String> values(OriginalCharacter c) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", c.getName()); fields.put("personality", c.getPersonality()); fields.put("memo", c.getMemo());
        fields.put("age", c.getAge()); fields.put("birthday", c.getBirthday()); fields.put("height", c.getHeight());
        fields.put("species", c.getSpecies()); fields.put("affiliation", c.getAffiliation()); fields.put("abilities", c.getAbilities());
        return fields;
    }
    static void copy(OriginalCharacter from, OriginalCharacter to) {
        to.setName(from.getName().strip()); to.setPersonality(from.getPersonality()); to.setMemo(from.getMemo());
        to.setAge(from.getAge()); to.setBirthday(from.getBirthday()); to.setHeight(from.getHeight());
        to.setSpecies(from.getSpecies()); to.setAffiliation(from.getAffiliation()); to.setAbilities(from.getAbilities());
        to.replaceTags(TagParser.parse(from.getTagsInput()));
    }
    static void read(Map<String, String> v, OriginalCharacter c) {
        c.setName(v.get("name")); c.setPersonality(v.get("personality")); c.setMemo(v.get("memo"));
        c.setAge(v.get("age")); c.setBirthday(v.get("birthday")); c.setHeight(v.get("height"));
        c.setSpecies(v.get("species")); c.setAffiliation(v.get("affiliation")); c.setAbilities(v.get("abilities"));
    }
    static void validate(OriginalCharacter c) {
        if (c.getName() == null || c.getName().isBlank()) throw new IllegalArgumentException("名前を入力してください。");
        values(c).forEach((key, value) -> {
            if (value != null && value.length() > limits().get(key)) throw new IllegalArgumentException(key + "の文字数が上限を超えています。");
        });
    }
}
