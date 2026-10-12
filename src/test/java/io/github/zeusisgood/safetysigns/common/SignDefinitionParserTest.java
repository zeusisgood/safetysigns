package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.Arrays;

import org.junit.Test;

public class SignDefinitionParserTest {

    private static SignDefinitionParser.Result parse(String namespace, String json) {
        return SignDefinitionParser.parse(namespace, new StringReader(json));
    }

    @Test
    public void readsAllFields() {
        SignDefinitionParser.Result result = parse("safetysigns", "{\"signs\":[{"
                + "\"id\":\"safetysigns:w003\",\"category\":\"W\",\"code\":\"W003\","
                + "\"texture\":\"safetysigns:signs/w003\","
                + "\"names\":{\"en_us\":\"Radioactive\",\"ja_JP\":\"放射性物質\"},"
                + "\"keywords\":[\"radiation\",\"原子炉\"]}]}");
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getDefinitions().size());
        SignDefinition d = result.getDefinitions().get(0);
        assertEquals("safetysigns:w003", d.getId());
        assertEquals(SignCategory.W, d.getCategory());
        assertEquals("W003", d.getCode());
        assertEquals("safetysigns:signs/w003", d.getTexture());
        assertEquals("放射性物質", d.getName("ja_jp"));
        assertEquals(Arrays.asList("radiation", "原子炉"), d.getKeywords());
    }

    @Test
    public void fillsDefaultsForCodeTextureAndName() {
        SignDefinition d = parse("pack", "{\"signs\":[{\"id\":\"pack:w100\",\"category\":\"w\"}]}")
                .getDefinitions().get(0);
        assertEquals("W100", d.getCode());
        assertEquals("pack:signs/w100", d.getTexture());
        // 名前がなければコード
        assertEquals("W100", d.getName("ja_jp"));
    }

    @Test
    public void nameFallsBackToEnglish() {
        SignDefinition d = parse("pack", "{\"signs\":[{\"id\":\"pack:w100\",\"category\":\"W\","
                + "\"names\":{\"en_us\":\"Warning\"}}]}").getDefinitions().get(0);
        assertEquals("Warning", d.getName("ja_jp"));
    }

    @Test
    public void skipsBadEntriesButKeepsOthers() {
        SignDefinitionParser.Result result = parse("pack", "{\"signs\":["
                + "{\"id\":\"bad id\",\"category\":\"W\"},"
                + "{\"id\":\"other:w001\",\"category\":\"W\"},"
                + "{\"id\":\"pack:w001\",\"category\":\"X\"},"
                + "{\"id\":[1],\"category\":\"W\"},"
                + "\"text\","
                + "{\"id\":\"pack:w002\",\"category\":\"W\"}]}");
        assertEquals(1, result.getDefinitions().size());
        assertEquals("pack:w002", result.getDefinitions().get(0).getId());
        assertEquals(5, result.getWarnings().size());
    }

    @Test
    public void reportsBrokenFiles() {
        assertTrue(parse("pack", "{not json").getDefinitions().isEmpty());
        assertFalse(parse("pack", "{not json").getWarnings().isEmpty());
        assertFalse(parse("pack", "[]").getWarnings().isEmpty());
        assertFalse(parse("pack", "{\"signs\":{}}").getWarnings().isEmpty());
    }
}
