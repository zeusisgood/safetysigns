package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/**
 * 看板の前面に図柄を重ねて描く。無地の板（ブロックモデル）の 0.003 ブロック手前に、図柄の四角を 1 枚貼る。
 * 無地なら何も描かない（モデルの無地の面がそのまま見える）。
 * 複数マスの看板は、代表のマス（正面から見て左下）の TileEntity が、32×32 の図柄を看板全体（2×2 なら 2×2 ブロック）に引き伸ばして描く。
 */
public class SignRenderer extends TileEntitySpecialRenderer<TileEntitySafetySign> {

    /** 板の前面の位置（ブロックの中心から北へ。モデルの板は z = 15/16〜1）と、重なりのちらつきを防ぐずらし。 */
    private static final double FRONT_Z = 15.0 / 16.0 - 0.5 - 0.003;

    @Override
    public void render(TileEntitySafetySign te, double x, double y, double z, float partialTicks, int destroyStage,
            float alpha) {
        String signId = te.getSignId();
        if (signId == null) {
            return;
        }
        IBlockState state = getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockSafetySign)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockSafetySign.FACING);
        SignLayout layout = ((BlockSafetySign) state.getBlock()).getLayout();
        TextureAtlasSprite sprite = SignTextures.sprite(signId);

        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        // blockstate の y 回転と同じ（北向きのモデルを基準に、東 90・南 180・西 270 度）
        GlStateManager.rotate(-(facing.getHorizontalAngle() + 180.0F), 0.0F, 1.0F, 0.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 北（-Z）から見て、画像の左上が左上に来るように（北から見ると +X が左）。
        // 代表のマスが左下なので、右（-X）と上（+Y）へ看板の幅・高さの分だけ広げる
        double left = 0.5;
        double right = 0.5 - layout.getWidth();
        double bottom = -0.5;
        double top = -0.5 + layout.getHeight();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(left, top, FRONT_Z).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        buffer.pos(left, bottom, FRONT_Z).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(right, bottom, FRONT_Z).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(right, top, FRONT_Z).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        tessellator.draw();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
