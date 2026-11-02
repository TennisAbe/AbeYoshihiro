package com.abeyoshihiro.character_manager;

import java.util.Locale;

/** Only validated RGB values may reach an inline CSS property. */
final class CardColors {
    private CardColors() { }
    static String normalize(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("背景色はRGBのカラーコードで指定してください。");
        return value.toLowerCase(Locale.ROOT);
    }
    static String rgb(String value) {
        if (value == null) return null;
        String hex = normalize(value);
        return Integer.parseInt(hex.substring(1, 3), 16) + ", " + Integer.parseInt(hex.substring(3, 5), 16) + ", " + Integer.parseInt(hex.substring(5, 7), 16);
    }
}
