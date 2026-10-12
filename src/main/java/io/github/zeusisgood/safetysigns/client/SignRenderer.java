package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.common.SignOutline;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
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

/**
 * 看板の板を描く。板は図柄のテクスチャの形（三角・円・四角）に切り抜き、厚さ 1 ピクセル。
 * <ul>
 *   <li>表: 図柄のテクスチャ 1 枚。透明な所は描かない（アルファテスト）。無地は無地のテクスチャ（四角）</li>
 *   <li>裏と縁: テクスチャの透明でない所の形（{@link SignOutline}）に合わせた灰色の金属板（{@code sign_back}）</li>
 * </ul>
 * 複数マスの看板は、代表のマス（正面から見て左下）の TileEntity が、図柄を面全体（2×2 なら 2×2 ブロック）に引き伸ばして描く。
 * 面の位置はブロックが決める（{@link BlockSafetySign#getFaceRow()}・{@link BlockSafetySign#getFaceZ()}。ポール付きは最上段の板だけ）。
 * ブロックモデルは板を持たない（ポール付きの支柱と金具だけ）。
 */
public class SignRenderer extends TileEntitySpecialRenderer<TileEntitySafetySign> {

    /** 板の厚さ（1 ピクセル）。 */
    private static final double THICKNESS = 1.0 / 16.0;

    // 面ごとの明るさ（バニラのブロックの陰影に近づける）。表は図柄が見やすいように明るさそのまま
    private static final float SHADE_FRONT = 1.0F;
    private static final float SHADE_BACK = 0.8F;
    private static final float SHADE_TOP = 1.0F;
    private static final float SHADE_BOTTOM = 0.5F;
    private static final float SHADE_SIDE = 0.7F;

    @Override
    public void render(TileEntitySafetySign te, double x, double y, double z, float partialTicks, int destroyStage,
            float alpha) {
        IBlockState state = getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockSafetySign)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockSafetySign.FACING);
        BlockSafetySign block = (BlockSafetySign) state.getBlock();
        SignLayout layout = block.getLayout();
        ResourceLocation texture = SignTextures.textureOf(te.getSignId());
        TextureAtlasSprite front = SignTextures.sprite(texture);
        TextureAtlasSprite back = SignTextures.sprite(SignTextures.BACK);
        SignOutline outline = SignTextures.outlineOf(texture);

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

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        // 表: 図柄のテクスチャ 1 枚（透明な所はアルファテストで描かれない）
        quad(buffer, SHADE_FRONT,
                left, top, frontZ, front.getMinU(), front.getMinV(),
                left, bottom, frontZ, front.getMinU(), front.getMaxV(),
                right, bottom, frontZ, front.getMaxU(), front.getMaxV(),
                right, top, frontZ, front.getMaxU(), front.getMinV());
        // 裏と縁: 灰色の無地なので、テクスチャはどこを取ってもよい（真ん中の 1 点）
        double u = back.getInterpolatedU(8);
        double v = back.getInterpolatedV(8);
        for (SignOutline.Rect rect : outline.getFaces()) {
            double x0 = plane.x(rect.x0);
            double x1 = plane.x(rect.x1);
            double y0 = plane.y(rect.y0);
            double y1 = plane.y(rect.y1);
            quad(buffer, SHADE_BACK, x0, y0, backZ, u, v, x0, y1, backZ, u, v, x1, y1, backZ, u, v, x1, y0, backZ, u, v);
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

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
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
