package io.github.zeusisgood.safetysigns.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 定義ファイルの 1 件の図柄。 */
public final class SignDefinition {

    /** 名前の言語が見つからないときに使う言語。 */
    public static final String FALLBACK_LANGUAGE = "en_us";

    private final String id;
    private final SignCategory category;
    private final String code;
    private final String texture;
    private final Map<String, String> names;
    private final List<String> keywords;

    public SignDefinition(String id, SignCategory category, String code, String texture,
            Map<String, String> names, List<String> keywords) {
        this.id = id;
        this.category = category;
        this.code = code;
        this.texture = texture;
        this.names = Collections.unmodifiableMap(names);
        this.keywords = Collections.unmodifiableList(keywords);
    }

    public String getId() {
        return id;
    }

    public SignCategory getCategory() {
        return category;
    }

    /** 表示用のコード（"W003" など）。 */
    public String getCode() {
        return code;
    }

    /** テクスチャ（ブロックのテクスチャの名前。例: {@code safetysigns:signs/w003}）。 */
    public String getTexture() {
        return texture;
    }

    /** 言語コード（"ja_jp" など）ごとの名前。 */
    public Map<String, String> getNames() {
        return names;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    /** 表示する言語の名前。なければ英語、それもなければコード。 */
    public String getName(String language) {
        String name = language == null ? null : names.get(language.toLowerCase(java.util.Locale.ROOT));
        if (name == null) {
            name = names.get(FALLBACK_LANGUAGE);
        }
        return name != null ? name : code;
    }
}
