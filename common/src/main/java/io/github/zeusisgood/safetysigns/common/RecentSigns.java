package io.github.zeusisgood.safetysigns.common;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 最近使った図柄の履歴（新しい順、{@link #MAX} 個まで）と、その保存の形（JSON）。
 * 一覧にない ID も数えて残す（パックを戻すとまた出る）。ファイルの場所と読み書きは各版の {@code client/RecentSignsFile}。
 */
public final class RecentSigns {

    /** 残す数（GUI の 3 行ぶん）。 */
    public static final int MAX = 18;
    /** 保存の形の版。形を変えたら上げる。 */
    public static final int FORMAT_VERSION = 1;

    private final List<String> ids = new ArrayList<>();

    /** 新しい順の図柄 ID。 */
    public List<String> getIds() {
        return Collections.unmodifiableList(ids);
    }

    /**
     * 先頭に足す。同じ ID があれば先頭へ移し、{@link #MAX} 個を超えた古いものを捨てる。
     * 形の正しくない ID は足さない。
     *
     * @return 履歴が変わったら true（もう先頭にあれば false）
     */
    public boolean add(String id) {
        if (!SignIds.isValid(id) || (!ids.isEmpty() && ids.get(0).equals(id))) {
            return false;
        }
        ids.remove(id);
        ids.add(0, id);
        while (ids.size() > MAX) {
            ids.remove(ids.size() - 1);
        }
        return true;
    }

    /**
     * JSON を読む。知らないキーは無視し、形の正しくない ID・重なった ID・{@link #MAX} 個を超えた分は読み飛ばす。
     * {@code recent} がなければ空。
     *
     * @throws JsonParseException JSON が壊れている・形が違う（呼ぶ側が空として始める）
     */
    public static RecentSigns read(Reader reader) {
        JsonElement root = new JsonParser().parse(reader);
        if (!root.isJsonObject()) {
            throw new JsonParseException("the root is not an object");
        }
        RecentSigns recent = new RecentSigns();
        JsonElement list = root.getAsJsonObject().get("recent");
        if (list == null) {
            return recent;
        }
        if (!list.isJsonArray()) {
            throw new JsonParseException("\"recent\" is not an array");
        }
        for (JsonElement element : list.getAsJsonArray()) {
            if (recent.ids.size() >= MAX) {
                break;
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String id = element.getAsString();
                if (SignIds.isValid(id) && !recent.ids.contains(id)) {
                    recent.ids.add(id);
                }
            }
        }
        return recent;
    }

    /** JSON で書く（{@code version} と {@code recent}）。 */
    public void write(Writer writer) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonArray list = new JsonArray();
        for (String id : ids) {
            list.add(id);
        }
        root.add("recent", list);
        new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
    }
}
