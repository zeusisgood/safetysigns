package io.github.zeusisgood.safetysigns.common;

/**
 * 図柄の分類（GUI のタブ）。ISO 7010 の 5 分類（W P M E F）と、GHS 絵表示（GHS）、そのほかの標識（OTHER）。
 * 並び順は GUI のタブの順。色は選択中のタブの色（0xRRGGBB）。
 */
public enum SignCategory {
    W(0xF9A800),
    P(0x9B2423),
    M(0x005387),
    E(0x237F52),
    F(0x9B2423),
    GHS(0xD7191F),
    OTHER(0x1A1A1A);

    private final int color;

    SignCategory(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    /** 定義ファイルの {@code category}（"W"・"GHS" など、大文字小文字を区別しない）から引く。知らない値なら null。 */
    public static SignCategory fromName(String name) {
        if (name == null) {
            return null;
        }
        for (SignCategory category : values()) {
            if (category.name().equalsIgnoreCase(name.trim())) {
                return category;
            }
        }
        return null;
    }
}
