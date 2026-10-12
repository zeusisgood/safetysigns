package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignAppearanceTest {

    @Test
    public void defaultIsBlankCutoutSilverBack() {
        assertNull(SignAppearance.DEFAULT.getSignId());
        assertTrue(SignAppearance.DEFAULT.isCutout());
        assertFalse(SignAppearance.DEFAULT.isBackPattern());
    }

    @Test
    public void emptyIdIsBlank() {
        assertEquals(SignAppearance.DEFAULT, new SignAppearance("", true, false));
    }

    @Test
    public void withChangesOnlyOneValue() {
        SignAppearance a = new SignAppearance("safetysigns:w003", true, false);
        assertEquals(new SignAppearance("safetysigns:w003", false, false), a.withCutout(false));
        assertEquals(new SignAppearance("safetysigns:w003", true, true), a.withBackPattern(true));
        assertEquals(new SignAppearance(null, true, false), a.withSignId(null));
        // 元の値は変わらない
        assertEquals("safetysigns:w003", a.getSignId());
    }

    @Test
    public void equalityCoversEveryValue() {
        SignAppearance a = new SignAppearance("safetysigns:w003", true, false);
        assertEquals(a.hashCode(), new SignAppearance("safetysigns:w003", true, false).hashCode());
        assertNotEquals(a, a.withCutout(false));
        assertNotEquals(a, a.withBackPattern(true));
        assertNotEquals(a, a.withSignId("safetysigns:w001"));
    }
}
