package io.github.zeusisgood.safetysigns.common;

/**
 * ISO 7010 の 5 分類。並び順は GUI のタブの順（W P M E F）。
 * 色は GUI のタブの安全色（0xRRGGBB）。
 */
public enum SignCategory {
    W(0xF9A800),
    P(0x9B2423),
    M(0x005387),
    E(0x237F52),
    F(0x9B2423);

    private final int color;

    SignCategory(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    /** 定義ファイルの {@code category}（"W" など、大文字小文字を区別しない）から引く。知らない値なら null。 */
    public static SignCategory fromLetter(String letter) {
        if (letter == null) {
            return null;
        }
        for (SignCategory category : values()) {
            if (category.name().equalsIgnoreCase(letter.trim())) {
                return category;
            }
        }
        return null;
    }
}
