package io.github.zeusisgood.safetysigns.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 看板の板の四角の並び（1.20.1・1.21.1 の描画が使う。1.12.2 は SignRenderer が同じものをディスプレイリストに描く）。
 * 座標は北を向いた看板のモデルで、代表のマスの中心が原点（ブロック単位。表は -z を向く）。
 * テクスチャの座標は、貼るテクスチャ（{@link Texture}）の中の割合（0〜1、左上が 0）で持ち、描くときに atlas の座標にする。
 * 四角の頂点は、外側（{@link Quad#nx} などの向き）から見て反時計回り（面の裏を省く描画でも見える向き）。
 * <ul>
 *   <li>切り抜き ON: 板は図柄のテクスチャの形（{@link SignOutline}）。表は図柄 1 枚で、透明な所は描かない</li>
 *   <li>切り抜き OFF: 板は四角。表は無地のテクスチャ（下地）の上に図柄を重ねる</li>
 *   <li>裏面 銀: 裏は板の形に合わせた灰色の金属板。裏面 図柄: 裏にも表と同じ図柄（と下地）を左右反転して貼る</li>
 *   <li>縁: いつも灰色</li>
 * </ul>
 */
public final class SignMesh {

    /** 板の厚さ（1 ピクセル）。 */
    public static final double THICKNESS = 1.0 / 16.0;

    // 面ごとの明るさ（バニラのブロックの陰影に近づける）。表は図柄が見やすいように明るさそのまま
    static final float SHADE_FRONT = 1.0F;
    static final float SHADE_BACK = 0.8F;
    static final float SHADE_TOP = 1.0F;
    static final float SHADE_BOTTOM = 0.5F;
    static final float SHADE_SIDE = 0.7F;

    /** 四角に貼るテクスチャ。 */
    public enum Texture {
        /** 図柄（無地なら無地のテクスチャ）。 */
        PATTERN,
        /** 切り抜き OFF の下地（無地のテクスチャ）。 */
        BASE,
        /** 裏面と縁の灰色（裏面のテクスチャの真ん中の 1 点）。 */
        BACK
    }

    /** 四角 1 枚。 */
    public static final class Quad {
        public final Texture texture;
        /** 表の図柄か（夜間に光る看板では、これだけを明るさ最大で描く。描く順も最後）。 */
        public final boolean front;
        /** 頂点の色（灰色の陰影。0〜1）。 */
        public final float shade;
        /** 4 頂点の x, y, z。 */
        public final float[] positions;
        /** 4 頂点の u, v（テクスチャの中の割合）。 */
        public final float[] uvs;
        public final float nx;
        public final float ny;
        public final float nz;

        Quad(Texture texture, boolean front, float shade, float[] positions, float[] uvs, float nx, float ny, float nz) {
            this.texture = texture;
            this.front = front;
            this.shade = shade;
            this.positions = positions;
            this.uvs = uvs;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    private SignMesh() {
    }

    /**
     * 看板 1 枚の四角。表の図柄（{@link Quad#front}）は最後に並ぶ（同じ位置の下地より上に出る）。
     *
     * @param outline     板の形（切り抜き ON は図柄のテクスチャから作った形。OFF なら四角を渡す）
     * @param width       看板の幅（マス）
     * @param height      看板の高さ（マス）
     * @param faceRow     図柄を描く面の下端の行（代表のマスから何マス上か）
     * @param faceZ       図柄を描く面（板の前面）の z（北を向いたモデルで、ブロックの北の端が 0）
     * @param base        表（と裏面が図柄なら裏）に下地を貼るか（切り抜き OFF で、無地でない）
     * @param backPattern 裏に図柄を貼るか
     */
    public static List<Quad> build(SignOutline outline, int width, int height, int faceRow, double faceZ,
            boolean base, boolean backPattern) {
        float frontZ = (float) (faceZ - 0.5);
        float backZ = (float) (frontZ + THICKNESS);
        // 北（-Z）から見て、画像の左上が左上に来るように（北から見ると +X が左）。
        // 代表のマスが左下なので、右（-X）と上（+Y）へ看板の幅・高さの分だけ広げる。下端は面の行から（ポール付きは最上段）
        float left = 0.5F;
        float right = 0.5F - width;
        float bottom = -0.5F + faceRow;
        float top = -0.5F + height;

        List<Quad> quads = new ArrayList<>();
        if (base) {
            quads.add(face(Texture.BASE, false, SHADE_FRONT, left, right, top, bottom, frontZ, -1, false));
        }
        if (backPattern) {
            // 裏: 表と同じ図柄を左右反転して貼る（裏から見ると、見る人の左が表の右になるため）
            if (base) {
                quads.add(face(Texture.BASE, false, SHADE_BACK, left, right, top, bottom, backZ, 1, true));
            }
            quads.add(face(Texture.PATTERN, false, SHADE_BACK, left, right, top, bottom, backZ, 1, true));
        } else {
            for (SignOutline.Rect rect : outline.getFaces()) {
                float x0 = x(left, right, rect.x0, outline.getWidth());
                float x1 = x(left, right, rect.x1, outline.getWidth());
                float y0 = y(top, bottom, rect.y0, outline.getHeight());
                float y1 = y(top, bottom, rect.y1, outline.getHeight());
                quads.add(solid(SHADE_BACK, new float[] {x0, y0, backZ, x0, y1, backZ, x1, y1, backZ, x1, y0, backZ}, 0, 0, 1));
            }
        }
        for (SignOutline.Edge edge : outline.getEdges()) {
            switch (edge.side) {
                case TOP:
                case BOTTOM: {
                    float ey = y(top, bottom, edge.at, outline.getHeight());
                    float x0 = x(left, right, edge.from, outline.getWidth());
                    float x1 = x(left, right, edge.to, outline.getWidth());
                    boolean up = edge.side == SignOutline.Side.TOP;
                    quads.add(solid(up ? SHADE_TOP : SHADE_BOTTOM,
                            new float[] {x0, ey, frontZ, x1, ey, frontZ, x1, ey, backZ, x0, ey, backZ}, 0, up ? 1 : -1, 0));
                    break;
                }
                default: {
                    float ex = x(left, right, edge.at, outline.getWidth());
                    float y0 = y(top, bottom, edge.from, outline.getHeight());
                    float y1 = y(top, bottom, edge.to, outline.getHeight());
                    // 画像の左（LEFT）は、北から見て左 = +X
                    int nx = edge.side == SignOutline.Side.LEFT ? 1 : -1;
                    quads.add(solid(SHADE_SIDE,
                            new float[] {ex, y0, frontZ, ex, y1, frontZ, ex, y1, backZ, ex, y0, backZ}, nx, 0, 0));
                    break;
                }
            }
        }
        quads.add(face(Texture.PATTERN, true, SHADE_FRONT, left, right, top, bottom, frontZ, -1, false));
        return Collections.unmodifiableList(quads);
    }

    /** テクスチャ 1 枚を面全体に貼る。mirrored は左右を反転する（裏から見て読める向き）。 */
    private static Quad face(Texture texture, boolean front, float shade, float left, float right, float top, float bottom,
            float z, int nz, boolean mirrored) {
        float uLeft = mirrored ? 1 : 0;
        float uRight = mirrored ? 0 : 1;
        return quad(texture, front, shade,
                new float[] {left, top, z, left, bottom, z, right, bottom, z, right, top, z},
                new float[] {uLeft, 0, uLeft, 1, uRight, 1, uRight, 0}, 0, 0, nz);
    }

    /** 灰色の四角（裏面のテクスチャの真ん中の 1 点を貼る）。 */
    private static Quad solid(float shade, float[] positions, int nx, int ny, int nz) {
        return quad(Texture.BACK, false, shade, positions, new float[] {0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F}, nx, ny, nz);
    }

    /** 頂点が外側（法線の向き）から見て反時計回りになるよう、逆回りなら並びを反転する。 */
    private static Quad quad(Texture texture, boolean front, float shade, float[] p, float[] uv, int nx, int ny, int nz) {
        float ax = p[3] - p[0];
        float ay = p[4] - p[1];
        float az = p[5] - p[2];
        float bx = p[6] - p[0];
        float by = p[7] - p[1];
        float bz = p[8] - p[2];
        float dot = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz;
        if (dot < 0) {
            // 0 1 2 3 → 0 3 2 1
            p = new float[] {p[0], p[1], p[2], p[9], p[10], p[11], p[6], p[7], p[8], p[3], p[4], p[5]};
            uv = new float[] {uv[0], uv[1], uv[6], uv[7], uv[4], uv[5], uv[2], uv[3]};
        }
        return new Quad(texture, front, shade, p, uv, nx, ny, nz);
    }

    /** テクスチャのピクセルの座標（左上が 0）から、看板の面の上の座標へ。 */
    private static float x(float left, float right, int px, int width) {
        return left + (right - left) * px / width;
    }

    private static float y(float top, float bottom, int py, int height) {
        return top + (bottom - top) * py / height;
    }
}
