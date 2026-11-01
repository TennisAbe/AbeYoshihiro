package com.abeyoshihiro.character_manager;

public enum CharacterWorld {
    ALTER_PALACE("オルタパレス"), OTHER_WORLD("異世界"), MODERN_WORLD("現代世界");
    private final String label;
    CharacterWorld(String label) { this.label = label; }
    public static CharacterWorld parse(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("特大分類が不正です。", exception); }
    }
    public String getLabel() { return label; }
}
