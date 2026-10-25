package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignAppearanceTest {

    @Test
    public void defaultIsBlankCutoutSilverBackGlowing() {
        assertNull(SignAppearance.DEFAULT.getSignId());
        assertTrue(SignAppearance.DEFAULT.isCutout());
        assertFalse(SignAppearance.DEFAULT.isBackPattern());
        assertTrue(SignAppearance.DEFAULT.isGlow());
    }

    @Test
    public void emptyIdIsBlank() {
        assertEquals(SignAppearance.DEFAULT, new SignAppearance("", true, false, true));
    }

    @Test
    public void withSignIdKeepsTheOtherValues() {
        SignAppearance a = new SignAppearance("safetysigns:w003", false, true, false);
        assertEquals(new SignAppearance("safetysigns:w001", false, true, false), a.withSignId("safetysigns:w001"));
        // 元の値は変わらない
        assertEquals("safetysigns:w003", a.getSignId());
    }

    @Test
    public void blankIsAlwaysCutout() {
        // 無地は切り抜きで見た目が変わらないので、ON にそろえる（裏面はそのまま）
        assertTrue(new SignAppearance(null, false, false, true).isCutout());
        assertEquals(SignAppearance.DEFAULT, new SignAppearance(null, false, false, true));
        assertEquals(new SignAppearance(null, true, true, true), new SignAppearance(null, false, true, true));
        assertEquals(new SignAppearance(null, true, true, true), new SignAppearance("safetysigns:w003", false, true, true).withSignId(null));
    }

    @Test
    public void equalityCoversEveryValue() {
        SignAppearance a = new SignAppearance("safetysigns:w003", true, false, true);
        assertEquals(a, new SignAppearance("safetysigns:w003", true, false, true));
        assertEquals(a.hashCode(), new SignAppearance("safetysigns:w003", true, false, true).hashCode());
        assertNotEquals(a, new SignAppearance("safetysigns:w003", false, false, true));
        assertNotEquals(a, new SignAppearance("safetysigns:w003", true, true, true));
        assertNotEquals(a, new SignAppearance("safetysigns:w001", true, false, true));
        assertNotEquals(a, new SignAppearance("safetysigns:w003", true, false, false));
    }

    @Test
    public void blankKeepsGlowOff() {
        // 無地も表の面は光るので、光らない設定はそのまま持つ（切り抜きと違って ON にそろえない）
        assertFalse(new SignAppearance(null, true, false, false).isGlow());
        assertNotEquals(SignAppearance.DEFAULT, new SignAppearance(null, true, false, false));
        assertFalse(new SignAppearance("safetysigns:w003", true, false, false).withSignId(null).isGlow());
    }
}
