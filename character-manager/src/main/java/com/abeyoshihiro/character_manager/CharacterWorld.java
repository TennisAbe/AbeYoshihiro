package com.abeyoshihiro.character_manager;

public enum CharacterWorld {
    PROTAGONIST("主人公"), ALTER_PALACE("オルタパレス"), OTHER_WORLD("異世界"), MODERN_WORLD("現代世界");
    private final String label;
    CharacterWorld(String label) { this.label = label; }
    public static CharacterWorld parse(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("特大分類が不正です。", exception); }
    }
    public static java.util.List<CharacterWorld> worlds() { return java.util.List.of(ALTER_PALACE, OTHER_WORLD, MODERN_WORLD); }
    public String getLabel() { return label; }
}
