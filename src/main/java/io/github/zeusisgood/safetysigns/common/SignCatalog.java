package io.github.zeusisgood.safetysigns.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 全パックの定義をマージした図柄の一覧（クライアントだけが持つ）。
 * 並びはカテゴリ（W P M E F）→ コードの順。
 */
public final class SignCatalog {

    public static final SignCatalog EMPTY = new SignCatalog(Collections.<SignDefinition>emptyList());

    private static final Comparator<SignDefinition> ORDER = Comparator
            .comparing(SignDefinition::getCategory)
            .thenComparing(SignDefinition::getCode, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SignDefinition::getId);

    private final List<SignDefinition> all;
    private final Map<String, SignDefinition> byId;

    private SignCatalog(List<SignDefinition> definitions) {
        List<SignDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(ORDER);
        this.all = Collections.unmodifiableList(sorted);
        Map<String, SignDefinition> map = new LinkedHashMap<>();
        for (SignDefinition definition : sorted) {
            map.put(definition.getId(), definition);
        }
        this.byId = map;
    }

    /**
     * パックごとの定義をマージする。
     *
     * @param packsLowToHigh 優先度の低いパックから順に（{@code IResourceManager.getAllResources} の返す順）。
     *                       同じ ID は後のパックの定義で丸ごと上書きする
     */
    public static SignCatalog merge(List<List<SignDefinition>> packsLowToHigh) {
        Map<String, SignDefinition> merged = new LinkedHashMap<>();
        for (List<SignDefinition> pack : packsLowToHigh) {
            for (SignDefinition definition : pack) {
                merged.put(definition.getId(), definition);
            }
        }
        return new SignCatalog(new ArrayList<>(merged.values()));
    }

    /** 一覧にある図柄。一覧にない ID（追加パックを入れていないなど）は null。呼ぶ側は「?」で表示し、ID は消さない。 */
    public SignDefinition get(String id) {
        return id == null ? null : byId.get(id);
    }

    public List<SignDefinition> all() {
        return all;
    }

    public List<SignDefinition> byCategory(SignCategory category) {
        List<SignDefinition> result = new ArrayList<>();
        for (SignDefinition definition : all) {
            if (definition.getCategory() == category) {
                result.add(definition);
            }
        }
        return result;
    }

    /**
     * 全カテゴリ横断の検索。空白で区切った語をすべて含むものを返す（AND）。空の検索語なら全部。
     * 対象はコード・ID・すべての言語の名前・キーワード。大文字小文字は区別しない。
     */
    public List<SignDefinition> search(String query) {
        String[] terms = query == null ? new String[0] : query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<SignDefinition> result = new ArrayList<>();
        for (SignDefinition definition : all) {
            if (matchesAll(definition, terms)) {
                result.add(definition);
            }
        }
        return result;
    }

    private static boolean matchesAll(SignDefinition definition, String[] terms) {
        for (String term : terms) {
            if (!term.isEmpty() && !matches(definition, term)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(SignDefinition definition, String term) {
        if (contains(definition.getCode(), term) || contains(definition.getId(), term)) {
            return true;
        }
        for (String name : definition.getNames().values()) {
            if (contains(name, term)) {
                return true;
            }
        }
        for (String keyword : definition.getKeywords()) {
            if (contains(keyword, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String text, String lowerTerm) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerTerm);
    }
}
