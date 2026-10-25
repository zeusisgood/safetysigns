package io.github.zeusisgood.safetysigns.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 定義ファイル（{@code assets/<namespace>/safetysigns/signs.json}）の 1 ファイルを読む。
 * 形のおかしい項目は飛ばして警告に積み、ほかの項目は読む（1 件の誤りでパック全体を捨てない）。
 */
public final class SignDefinitionParser {

    private SignDefinitionParser() {
    }

    /** 1 ファイルを読んだ結果。 */
    public static final class Result {
        private final List<SignDefinition> definitions;
        private final List<String> warnings;

        Result(List<SignDefinition> definitions, List<String> warnings) {
            this.definitions = definitions;
            this.warnings = warnings;
        }

        public List<SignDefinition> getDefinitions() {
            return definitions;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    /**
     * @param namespace ファイルが置かれている名前空間。項目の ID の名前空間はこれと同じでなければならない
     *                  （どのパックがどの ID を定義できるかをはっきりさせ、上書きの順番を一意にするため）
     */
    public static Result parse(String namespace, Reader reader) {
        List<SignDefinition> definitions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonArray signs;
        try {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("signs")
                    || !root.getAsJsonObject().get("signs").isJsonArray()) {
                warnings.add("\"signs\" の配列がない");
                return new Result(definitions, warnings);
            }
            signs = root.getAsJsonObject().getAsJsonArray("signs");
        } catch (JsonParseException | IllegalStateException e) {
            warnings.add("JSON として読めない: " + e.getMessage());
            return new Result(definitions, warnings);
        }

        for (int i = 0; i < signs.size(); i++) {
            JsonElement element = signs.get(i);
            if (!element.isJsonObject()) {
                warnings.add("signs[" + i + "]: オブジェクトでない");
                continue;
            }
            try {
                SignDefinition definition = parseEntry(namespace, element.getAsJsonObject(), i, warnings);
                if (definition != null) {
                    definitions.add(definition);
                }
            } catch (IllegalStateException | ClassCastException | UnsupportedOperationException e) {
                // 文字列のはずの所に配列があるなど
                warnings.add("signs[" + i + "]: 値の型が違う: " + e.getMessage());
            }
        }
        return new Result(definitions, warnings);
    }

    private static SignDefinition parseEntry(String namespace, JsonObject json, int index, List<String> warnings) {
        String where = "signs[" + index + "]";
        String id = getString(json, "id");
        if (!SignIds.isValid(id)) {
            warnings.add(where + ": id の形が違う: " + id);
            return null;
        }
        where = where + " (" + id + ")";
        if (!SignIds.namespace(id).equals(namespace)) {
            warnings.add(where + ": id の名前空間がファイルの名前空間 " + namespace + " と違う");
            return null;
        }
        String categoryName = getString(json, "category");
        if (categoryName == null || categoryName.trim().isEmpty()) {
            warnings.add(where + ": category がない");
            return null;
        }
        SignCategory category = SignCategory.fromName(categoryName);
        if (category == null) {
            // 知らない分類（新しい版の Mod 向けのパックなど）は捨てずに「その他」のタブに出す
            warnings.add(where + ": category " + categoryName + " を知らないので OTHER として扱う");
            category = SignCategory.OTHER;
        }
        String code = getString(json, "code");
        if (code == null || code.isEmpty()) {
            // コードがなければ ID の名前を大文字にして使う
            code = id.substring(id.indexOf(':') + 1).toUpperCase(Locale.ROOT);
        }
        String texture = getString(json, "texture");
        if (texture == null || texture.isEmpty()) {
            // 省略したら <namespace>:signs/<名前>
            texture = namespace + ":signs/" + id.substring(id.indexOf(':') + 1);
        }

        Map<String, String> names = new LinkedHashMap<>();
        if (json.has("names") && json.get("names").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("names").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    names.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
                }
            }
        }
        List<String> keywords = new ArrayList<>();
        if (json.has("keywords") && json.get("keywords").isJsonArray()) {
            for (JsonElement keyword : json.getAsJsonArray("keywords")) {
                if (keyword.isJsonPrimitive()) {
                    keywords.add(keyword.getAsString());
                }
            }
        }
        return new SignDefinition(id, category, code, texture, names, keywords);
    }

    private static String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
