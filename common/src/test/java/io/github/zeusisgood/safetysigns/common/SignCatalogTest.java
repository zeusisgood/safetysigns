package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SignCatalogTest {

    private static SignDefinition sign(String id, SignCategory category, String code, String en, String ja,
            String... keywords) {
        Map<String, String> names = new HashMap<>();
        names.put("en_us", en);
        names.put("ja_jp", ja);
        return new SignDefinition(id, category, code, "x:signs/" + code, names, Arrays.asList(keywords));
    }

    private static final SignDefinition W003 = sign("safetysigns:w003", SignCategory.W, "W003",
            "Radioactive material or ionizing radiation", "放射性物質・電離放射線", "radiation", "原子炉");
    private static final SignDefinition W012 = sign("safetysigns:w012", SignCategory.W, "W012",
            "Electricity", "感電注意", "electric", "電気");
    private static final SignDefinition W001 = sign("safetysigns:w001", SignCategory.W, "W001",
            "General warning sign", "一般警告");
    private static final SignDefinition P004 = sign("safetysigns:p004", SignCategory.P, "P004",
            "No thoroughfare", "通行禁止");
    private static final SignDefinition M010 = sign("safetysigns:m010", SignCategory.M, "M010",
            "Wear protective clothing", "防護服着用", "radiation suit");

    private static SignCatalog catalog(SignDefinition... definitions) {
        return SignCatalog.merge(Collections.singletonList(Arrays.asList(definitions)));
    }

    private static List<String> ids(List<SignDefinition> definitions) {
        List<String> ids = new ArrayList<>();
        for (SignDefinition d : definitions) {
            ids.add(d.getId());
        }
        return ids;
    }

    @Test
    public void ordersByCategoryThenCode() {
        SignCatalog c = catalog(M010, W012, P004, W003, W001);
        assertEquals(Arrays.asList("safetysigns:w001", "safetysigns:w003", "safetysigns:w012",
                "safetysigns:p004", "safetysigns:m010"), ids(c.all()));
        assertEquals(Arrays.asList("safetysigns:p004"), ids(c.byCategory(SignCategory.P)));
        assertTrue(c.byCategory(SignCategory.E).isEmpty());
    }

    @Test
    public void laterPackOverridesSameId() {
        SignDefinition replaced = sign("safetysigns:w003", SignCategory.W, "W003", "Replaced", "置き換え");
        SignDefinition added = sign("extra:w100", SignCategory.W, "W100", "Added", "追加");
        SignCatalog c = SignCatalog.merge(Arrays.asList(
                Arrays.asList(W001, W003),
                Arrays.asList(replaced, added)));
        assertEquals(3, c.all().size());
        assertSame(replaced, c.get("safetysigns:w003"));
        assertSame(W001, c.get("safetysigns:w001"));
        assertSame(added, c.get("extra:w100"));
    }

    @Test
    public void unknownIdIsNullAndNotAnError() {
        SignCatalog c = catalog(W003);
        assertNull(c.get("extra:w999"));
        assertNull(c.get(null));
        assertNull(SignCatalog.EMPTY.get("safetysigns:w003"));
    }

    @Test
    public void searchMatchesCodeCaseInsensitively() {
        SignCatalog c = catalog(W001, W003, W012, P004, M010);
        assertEquals(Arrays.asList("safetysigns:w003"), ids(c.search("w003")));
        assertEquals(Arrays.asList("safetysigns:w003"), ids(c.search("W003")));
        assertEquals(Arrays.asList("safetysigns:p004"), ids(c.search("p0")));
    }

    @Test
    public void searchMatchesBothLanguagesAndKeywordsAcrossCategories() {
        SignCatalog c = catalog(W001, W003, W012, P004, M010);
        // 日本語名
        assertEquals(Arrays.asList("safetysigns:p004"), ids(c.search("通行")));
        // 英語名（大文字小文字を区別しない）
        assertEquals(Arrays.asList("safetysigns:w012"), ids(c.search("ELECTRICITY")));
        // キーワード。カテゴリをまたいで W と M の両方
        assertEquals(Arrays.asList("safetysigns:w003", "safetysigns:m010"), ids(c.search("radiation")));
        assertEquals(Arrays.asList("safetysigns:w003"), ids(c.search("原子炉")));
    }

    @Test
    public void searchRequiresAllTerms() {
        SignCatalog c = catalog(W001, W003, W012, P004, M010);
        assertEquals(Arrays.asList("safetysigns:m010"), ids(c.search("radiation  suit")));
        assertTrue(c.search("radiation 通行").isEmpty());
    }

    @Test
    public void emptySearchReturnsAll() {
        SignCatalog c = catalog(W001, P004);
        assertEquals(2, c.search("").size());
        assertEquals(2, c.search("   ").size());
        assertEquals(2, c.search(null).size());
    }
}
