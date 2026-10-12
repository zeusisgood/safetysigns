package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.common.SignOutline;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * 看板の板を描く。厚さ 1 ピクセル。見た目は看板ごとの設定（{@link SignAppearance}）で変わる。
 * <ul>
 *   <li>切り抜き ON（既定）: 板は図柄のテクスチャの形（三角・円・四角）。表は図柄 1 枚で、透明な所は描かない（アルファテスト）</li>
 *   <li>切り抜き OFF: 板は四角。表は無地のテクスチャ（下地）の上に図柄を重ねる</li>
 *   <li>裏面 銀（既定）: 裏は板の形（{@link SignOutline}）に合わせた灰色の金属板（{@code sign_back}）</li>
 *   <li>裏面 図柄: 裏にも表と同じ図柄（切り抜き OFF なら下地も）を左右反転して貼る（裏から見て読める向き）</li>
 *   <li>縁: いつも灰色</li>
 * </ul>
 * 複数マスの看板は、代表のマス（正面から見て左下）の TileEntity が、図柄を面全体（2×2 なら 2×2 ブロック）に引き伸ばして描く。
 * 面の位置はブロックが決める（{@link BlockSafetySign#getFaceRow()}・{@link BlockSafetySign#getFaceZ()}。ポール付きは最上段の板だけ）。
 * ブロックモデルは板を持たない（ポール付きの支柱と金具だけ）。
 * 板の形はテクスチャ・看板の種類・切り抜き・裏面ごとに 1 回だけディスプレイリストに組み立て、毎フレームそれを呼ぶ（テクスチャを貼り直したら作り直す）。
 */
public class SignRenderer extends TileEntitySpecialRenderer<TileEntitySafetySign> {

    /** 切り抜き OFF のときの板の形（四角）。 */
    private static final SignOutline SQUARE = SignOutline.square(16);

    /** 板の厚さ（1 ピクセル）。 */
    private static final double THICKNESS = 1.0 / 16.0;

    // 面ごとの明るさ（バニラのブロックの陰影に近づける）。表は図柄が見やすいように明るさそのまま
    private static final float SHADE_FRONT = 1.0F;
    private static final float SHADE_BACK = 0.8F;
    private static final float SHADE_TOP = 1.0F;
    private static final float SHADE_BOTTOM = 0.5F;
    private static final float SHADE_SIDE = 0.7F;

    /** 取っておいた板の形（ディスプレイリストの番号）。キーはテクスチャと看板のブロック（大きさと面の位置が決まる）。 */
    private final Map<ShapeKey, Integer> displayLists = new HashMap<>();
    /** displayLists を作ったときの、テクスチャを貼り直した回数。 */
    private int listGeneration = -1;

    @Override
    public void render(TileEntitySafetySign te, double x, double y, double z, float partialTicks, int destroyStage,
            float alpha) {
        IBlockState state = getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockSafetySign)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockSafetySign.FACING);
        BlockSafetySign block = (BlockSafetySign) state.getBlock();
        SignAppearance appearance = te.getAppearance();
        ResourceLocation texture = SignTextures.textureOf(appearance.getSignId());

        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        // blockstate の y 回転と同じ（北向きのモデルを基準に、東 90・南 180・西 270 度）
        GlStateManager.rotate(-(facing.getHorizontalAngle() + 180.0F), 0.0F, 1.0F, 0.0F);
        GlStateManager.disableLighting();
        // 向きを気にせずに済むよう、裏向きの面も描く（表と裏は厚さの分だけ離れているので、手前の面だけが見える）
        GlStateManager.disableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (block.getFaceRow() > 0) {
            // 明るさは板のあるマスで取る（既定は代表のマス＝ポール付きでは最下段の支柱）
            int light = getWorld().getCombinedLight(te.getPos().up(block.getFaceRow()), 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
        }

        // 板の形は、テクスチャと看板の種類ごとに 1 回だけ組み立てて、毎フレームそれを呼ぶ
        // （高解像度のリソースパックだと、1 枚の板が数百〜千の四角になるため）。明るさと向きは毎回ここで決める
        GlStateManager.callList(displayList(texture, block, appearance.isCutout(), appearance.isBackPattern()));
        // リストの頂点色（灰色の陰影）が GL の今の色に残る。GlStateManager の覚えている色を捨て、次の color() が必ず届くようにする
        GlStateManager.resetColor();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** 板の形のディスプレイリスト。テクスチャを貼り直したら（図柄の場所と形が変わるので）全部作り直す。 */
    private int displayList(ResourceLocation texture, BlockSafetySign block, boolean cutout, boolean backPattern) {
        int generation = SignTextures.generation();
        if (generation != listGeneration) {
            for (int list : displayLists.values()) {
                GLAllocation.deleteDisplayLists(list);
            }
            displayLists.clear();
            listGeneration = generation;
        }
        ShapeKey key = new ShapeKey(texture, block, cutout, backPattern);
        Integer list = displayLists.get(key);
        if (list == null) {
            list = GLAllocation.generateDisplayLists(1);
            boolean compiled = false;
            GlStateManager.glNewList(list, GL11.GL_COMPILE);
            try {
                drawShape(texture, block, cutout, backPattern);
                compiled = true;
            } finally {
                // 途中で失敗してもリストを閉じる（開いたままだと以後の GL がおかしくなる）。失敗したリストは捨てる
                GlStateManager.glEndList();
                if (!compiled) {
                    GLAllocation.deleteDisplayLists(list);
                }
            }
            displayLists.put(key, list);
        }
        return list;
    }

    /**
     * 板の表・裏・縁を描く（ブロックの中心が原点、北向き）。ディスプレイリストに記録するために呼ぶ。
     * 切り抜き OFF なら形は四角で、表（と裏面が図柄なら裏）は無地の下地の上に図柄を重ねる。
     * 裏面が図柄なら、裏は灰色の代わりに図柄を左右反転して貼る（裏から見て読める向き）。
     */
    private static void drawShape(ResourceLocation texture, BlockSafetySign block, boolean cutout, boolean backPattern) {
        SignLayout layout = block.getLayout();
        TextureAtlasSprite front = SignTextures.sprite(texture);
        TextureAtlasSprite back = SignTextures.sprite(SignTextures.BACK);
        // 下地（切り抜き OFF のとき図柄の下に貼る）。無地の看板は図柄そのものが下地なので重ねない
        TextureAtlasSprite base = !cutout && !texture.equals(SignTextures.BLANK) ? SignTextures.sprite(SignTextures.BLANK) : null;
        SignOutline outline = cutout ? SignTextures.outlineOf(texture) : SQUARE;

        // ブロックの中心を原点にした、板の表と裏の z（表は -z を向く）
        double frontZ = block.getFaceZ() - 0.5;
        double backZ = frontZ + THICKNESS;
        // 北（-Z）から見て、画像の左上が左上に来るように（北から見ると +X が左）。
        // 代表のマスが左下なので、右（-X）と上（+Y）へ看板の幅・高さの分だけ広げる。下端は面の行から（ポール付きは最上段）
        double left = 0.5;
        double right = 0.5 - layout.getWidth();
        double bottom = -0.5 + block.getFaceRow();
        double top = -0.5 + layout.getHeight();
        Plane plane = new Plane(left, right, top, bottom, outline.getWidth(), outline.getHeight());

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        // 表: 図柄のテクスチャ 1 枚（透明な所はアルファテストで描かれない）。切り抜き OFF なら先に下地を貼る
        // （同じ位置の面は、深さの比べ方が「以下」なのであとに描いたほうが上に出る）
        if (base != null) {
            face(buffer, SHADE_FRONT, base, left, right, top, bottom, frontZ, false);
        }
        face(buffer, SHADE_FRONT, front, left, right, top, bottom, frontZ, false);
        // 裏と縁の灰色: 無地なので、テクスチャはどこを取ってもよい（真ん中の 1 点）
        double u = back.getInterpolatedU(8);
        double v = back.getInterpolatedV(8);
        if (backPattern) {
            // 裏: 表と同じ図柄を左右反転して貼る（裏から見ると、見る人の左が表の右になるため）
            if (base != null) {
                face(buffer, SHADE_BACK, base, left, right, top, bottom, backZ, true);
            }
            face(buffer, SHADE_BACK, front, left, right, top, bottom, backZ, true);
        } else {
            for (SignOutline.Rect rect : outline.getFaces()) {
                double x0 = plane.x(rect.x0);
                double x1 = plane.x(rect.x1);
                double y0 = plane.y(rect.y0);
                double y1 = plane.y(rect.y1);
                quad(buffer, SHADE_BACK, x0, y0, backZ, u, v, x0, y1, backZ, u, v, x1, y1, backZ, u, v, x1, y0, backZ, u, v);
            }
        }
        for (SignOutline.Edge edge : outline.getEdges()) {
            if (edge.side == SignOutline.Side.TOP || edge.side == SignOutline.Side.BOTTOM) {
                double ey = plane.y(edge.at);
                double x0 = plane.x(edge.from);
                double x1 = plane.x(edge.to);
                float shade = edge.side == SignOutline.Side.TOP ? SHADE_TOP : SHADE_BOTTOM;
                quad(buffer, shade, x0, ey, frontZ, u, v, x1, ey, frontZ, u, v, x1, ey, backZ, u, v, x0, ey, backZ, u, v);
            } else {
                double ex = plane.x(edge.at);
                double y0 = plane.y(edge.from);
                double y1 = plane.y(edge.to);
                quad(buffer, SHADE_SIDE, ex, y0, frontZ, u, v, ex, y1, frontZ, u, v, ex, y1, backZ, u, v, ex, y0, backZ, u, v);
            }
        }
        tessellator.draw();
    }

    /** テクスチャ 1 枚を面全体に貼る。mirrored は左右を反転する（裏から見て読める向き）。 */
    private static void face(BufferBuilder buffer, float shade, TextureAtlasSprite sprite,
            double left, double right, double top, double bottom, double z, boolean mirrored) {
        double uLeft = mirrored ? sprite.getMaxU() : sprite.getMinU();
        double uRight = mirrored ? sprite.getMinU() : sprite.getMaxU();
        quad(buffer, shade,
                left, top, z, uLeft, sprite.getMinV(),
                left, bottom, z, uLeft, sprite.getMaxV(),
                right, bottom, z, uRight, sprite.getMaxV(),
                right, top, z, uRight, sprite.getMinV());
    }

    private static void quad(BufferBuilder buffer, float shade,
            double x0, double y0, double z0, double u0, double v0,
            double x1, double y1, double z1, double u1, double v1,
            double x2, double y2, double z2, double u2, double v2,
            double x3, double y3, double z3, double u3, double v3) {
        buffer.pos(x0, y0, z0).tex(u0, v0).color(shade, shade, shade, 1.0F).endVertex();
        buffer.pos(x1, y1, z1).tex(u1, v1).color(shade, shade, shade, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).tex(u2, v2).color(shade, shade, shade, 1.0F).endVertex();
        buffer.pos(x3, y3, z3).tex(u3, v3).color(shade, shade, shade, 1.0F).endVertex();
    }

    /** ディスプレイリストのキー: テクスチャ・看板のブロック（種類ごとに 1 つなので、同じかどうかで比べる）・切り抜き・裏面。 */
    private static final class ShapeKey {
        private final ResourceLocation texture;
        private final BlockSafetySign block;
        private final boolean cutout;
        private final boolean backPattern;

        ShapeKey(ResourceLocation texture, BlockSafetySign block, boolean cutout, boolean backPattern) {
            this.texture = texture;
            this.block = block;
            this.cutout = cutout;
            this.backPattern = backPattern;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ShapeKey)) {
                return false;
            }
            ShapeKey key = (ShapeKey) other;
            return texture.equals(key.texture) && block == key.block && cutout == key.cutout && backPattern == key.backPattern;
        }

        @Override
        public int hashCode() {
            return ((texture.hashCode() * 31 + System.identityHashCode(block)) * 2 + (cutout ? 1 : 0)) * 2 + (backPattern ? 1 : 0);
        }
    }

    /** テクスチャのピクセルの座標（左上が 0）から、看板の面の上の座標へ。 */
    private static final class Plane {
        private final double left;
        private final double right;
        private final double top;
        private final double bottom;
        private final int width;
        private final int height;

        Plane(double left, double right, double top, double bottom, int width, int height) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.width = width;
            this.height = height;
        }

        double x(int px) {
            return left + (right - left) * px / width;
        }

        double y(int py) {
            return top + (bottom - top) * py / height;
        }
    }
}
