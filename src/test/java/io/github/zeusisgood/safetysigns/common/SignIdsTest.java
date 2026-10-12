package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignIdsTest {

    @Test
    public void acceptsNamespacedIds() {
        assertTrue(SignIds.isValid("safetysigns:w003"));
        assertTrue(SignIds.isValid("my_pack:extra/w100"));
        assertTrue(SignIds.isValid("a.b-c:d_e.f-g"));
    }

    @Test
    public void rejectsWrongForms() {
        assertFalse(SignIds.isValid(null));
        assertFalse(SignIds.isValid(""));
        assertFalse(SignIds.isValid("w003"));
        assertFalse(SignIds.isValid(":w003"));
        assertFalse(SignIds.isValid("safetysigns:"));
        assertFalse(SignIds.isValid("safetysigns:w:003"));
        assertFalse(SignIds.isValid("SafetySigns:W003"));
        assertFalse(SignIds.isValid("safetysigns:w 003"));
        assertFalse(SignIds.isValid("safetysigns:放射線"));
        assertFalse(SignIds.isValid("my/pack:w003"));
    }

    @Test
    public void limitsLengthTo64() {
        String prefix = "safetysigns:";
        String ok = prefix + repeat('a', SignIds.MAX_LENGTH - prefix.length());
        assertEquals(64, ok.length());
        assertTrue(SignIds.isValid(ok));
        assertFalse(SignIds.isValid(ok + "a"));
    }

    @Test
    public void extractsNamespace() {
        assertEquals("safetysigns", SignIds.namespace("safetysigns:w003"));
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
