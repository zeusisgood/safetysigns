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
    public void withSignIdKeepsTheOtherValues() {
        SignAppearance a = new SignAppearance("safetysigns:w003", false, true);
        assertEquals(new SignAppearance("safetysigns:w001", false, true), a.withSignId("safetysigns:w001"));
        // 元の値は変わらない
        assertEquals("safetysigns:w003", a.getSignId());
    }

    @Test
    public void blankIsAlwaysCutout() {
        // 無地は切り抜きで見た目が変わらないので、ON にそろえる（裏面はそのまま）
        assertTrue(new SignAppearance(null, false, false).isCutout());
        assertEquals(SignAppearance.DEFAULT, new SignAppearance(null, false, false));
        assertEquals(new SignAppearance(null, true, true), new SignAppearance(null, false, true));
        assertEquals(new SignAppearance(null, true, true), new SignAppearance("safetysigns:w003", false, true).withSignId(null));
    }

    @Test
    public void equalityCoversEveryValue() {
        SignAppearance a = new SignAppearance("safetysigns:w003", true, false);
        assertEquals(a, new SignAppearance("safetysigns:w003", true, false));
        assertEquals(a.hashCode(), new SignAppearance("safetysigns:w003", true, false).hashCode());
        assertNotEquals(a, new SignAppearance("safetysigns:w003", false, false));
        assertNotEquals(a, new SignAppearance("safetysigns:w003", true, true));
        assertNotEquals(a, new SignAppearance("safetysigns:w001", true, false));
    }
}
