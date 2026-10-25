package io.github.zeusisgood.safetysigns.common;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SignOutlineTest {

    /** "#" が不透明、"." が透明（上の行から）。 */
    private static SignOutline outline(String... rows) {
        int width = rows[0].length();
        boolean[] solid = new boolean[width * rows.length];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                solid[y * width + x] = rows[y].charAt(x) == '#';
            }
        }
        return SignOutline.of(width, rows.length, solid);
    }

    private static int area(List<SignOutline.Rect> faces) {
        int area = 0;
        for (SignOutline.Rect rect : faces) {
            area += (rect.x1 - rect.x0) * (rect.y1 - rect.y0);
        }
        return area;
    }

    private static int edgeLength(SignOutline outline, SignOutline.Side side) {
        int length = 0;
        for (SignOutline.Edge edge : outline.getEdges()) {
            if (edge.side == side) {
                length += edge.to - edge.from;
            }
        }
        return length;
    }

    @Test
    public void fullSquareIsOneRectAndFourEdges() {
        SignOutline outline = SignOutline.square(16);
        assertEquals(1, outline.getFaces().size());
        assertEquals("Rect(0, 0, 16, 16)", outline.getFaces().get(0).toString());
        assertEquals(4, outline.getEdges().size());
        for (SignOutline.Side side : SignOutline.Side.values()) {
            assertEquals(16, edgeLength(outline, side));
        }
    }

    @Test
    public void triangleCoversOnlySolidPixels() {
        SignOutline outline = outline(
                "..#..",
                ".###.",
                "#####");
        assertEquals(9, area(outline.getFaces()));
        // 上向きの縁は各行の出っ張り（1 + 1 + 1 + 2 = 5）、下向きは底辺だけ
        assertEquals(5, edgeLength(outline, SignOutline.Side.TOP));
        assertEquals(5, edgeLength(outline, SignOutline.Side.BOTTOM));
        assertEquals(3, edgeLength(outline, SignOutline.Side.LEFT));
        assertEquals(3, edgeLength(outline, SignOutline.Side.RIGHT));
    }

    @Test
    public void sameRowsMergeIntoOneRect() {
        SignOutline outline = outline(
                "....",
                ".##.",
                ".##.",
                "....");
        assertEquals(1, outline.getFaces().size());
        assertEquals("Rect(1, 1, 3, 3)", outline.getFaces().get(0).toString());
    }

    @Test
    public void differentRowsStartNewRects() {
        SignOutline outline = outline(
                "##.",
                "###",
                "###");
        // 1 行目と 2・3 行目は幅が違うので別の四角（2・3 行目はまとまる）
        assertEquals(2, outline.getFaces().size());
        assertEquals(8, area(outline.getFaces()));
        assertEquals(3, edgeLength(outline, SignOutline.Side.TOP));
        assertEquals(3, edgeLength(outline, SignOutline.Side.RIGHT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongPixelCount() {
        SignOutline.fromArgb(2, 2, new int[3]);
    }

    @Test
    public void holeHasInnerEdges() {
        SignOutline outline = outline(
                "###",
                "#.#",
                "###");
        assertEquals(8, area(outline.getFaces()));
        // 外周 3 + 穴の縁 1
        assertEquals(4, edgeLength(outline, SignOutline.Side.TOP));
        assertEquals(4, edgeLength(outline, SignOutline.Side.LEFT));
    }

    @Test
    public void transparentImageHasNothing() {
        SignOutline outline = SignOutline.fromArgb(2, 2, new int[4]);
        assertTrue(outline.getFaces().isEmpty());
        assertTrue(outline.getEdges().isEmpty());
    }

    @Test
    public void alphaThresholdMatchesAlphaTest() {
        // アルファ 25 は透明、26 は不透明（描画のアルファテスト GREATER 0.1 と同じ）
        SignOutline outline = SignOutline.fromArgb(2, 1, new int[] {25 << 24 | 0xFFFFFF, 26 << 24});
        assertEquals(1, outline.getFaces().size());
        assertEquals("Rect(1, 0, 2, 1)", outline.getFaces().get(0).toString());
    }
}
