package io.github.zeusisgood.safetysigns.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignMesh;
import io.github.zeusisgood.safetysigns.common.SignOutline;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 看板の板を描く。厚さ 1 ピクセル。見た目は看板ごとの設定（{@link SignAppearance}）で変わる（四角の並びは {@link SignMesh}）。
 * <ul>
 *   <li>切り抜き ON（既定）: 板は図柄のテクスチャの形（三角・円・四角）。表は図柄 1 枚で、透明な所は描かない（cutout）</li>
 *   <li>切り抜き OFF: 板は四角。表は無地のテクスチャ（下地）の上に図柄を重ねる</li>
 *   <li>裏面 銀（既定）: 裏は板の形に合わせた灰色の金属板。裏面 図柄: 裏にも表と同じ図柄を左右反転して貼る</li>
 *   <li>縁: いつも灰色</li>
 *   <li>夜間に光る（既定）: 表の図柄だけを明るさ最大で描く。周りは照らさない。下地・裏・縁は、ポール・金具（ブロックモデル）と同じく周りの明るさ</li>
 * </ul>
 * 複数マスの看板は、代表のマス（正面から見て左下）の TileEntity が、図柄を面全体（2×2 なら 2×2 ブロック）に引き伸ばして描く。
 * 面の位置はブロックが決める（{@link BlockSafetySign#getFaceRow()}・{@link BlockSafetySign#getFaceZ()}。ポール付きは最上段の板だけ）。
 * 四角の並びはテクスチャ・看板の種類・切り抜き・裏面ごとに 1 回だけ作って取っておく（リソースを読み直したら作り直す）。
 * 陰影は頂点の色で付けるので、法線で明るさを変えないブロックの描き方（{@link RenderType#cutout()}）で描く。
 */
public class SignRenderer implements BlockEntityRenderer<TileEntitySafetySign> {

    /** 切り抜き OFF のときの板の形（四角）。 */
    private static final SignOutline SQUARE = SignOutline.square(16);

    /** 取っておいた四角の並び。 */
    private final Map<ShapeKey, List<SignMesh.Quad>> meshes = new HashMap<>();
    /** meshes を作ったときの、リソースを読み直した回数。 */
    private int meshGeneration = -1;

    public SignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileEntitySafetySign te, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        Level world = te.getLevel();
        BlockState state = te.getBlockState();
        if (world == null || !(state.getBlock() instanceof BlockSafetySign)) {
            return;
        }
        Direction facing = state.getValue(BlockSafetySign.FACING);
        BlockSafetySign block = (BlockSafetySign) state.getBlock();
        SignAppearance appearance = te.getAppearance();
        ResourceLocation texture = SignTextures.textureOf(appearance.getSignId());
        List<SignMesh.Quad> quads = mesh(texture, block, appearance.isCutout(), appearance.isBackPattern());

        TextureAtlasSprite pattern = SignTextures.sprite(texture);
        TextureAtlasSprite base = SignTextures.sprite(SignTextures.BLANK);
        TextureAtlasSprite back = SignTextures.sprite(SignTextures.BACK);
        // 明るさは板のあるマスで取る（ポール付きでは、代表のマス＝最下段の支柱ではなく最上段）
        int light = LevelRenderer.getLightColor(world, te.getBlockPos().above(block.getFaceRow()));
        // 表の図柄だけを明るさ最大で描く（ライトマップの値を変えるだけなので、周りは照らさない）
        int frontLight = appearance.isGlow() ? LightTexture.FULL_BRIGHT : light;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // blockstate の y 回転と同じ（北向きのモデルを基準に、東 90・南 180・西 270 度）
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        // 下地の上に図柄が出るよう、並びの順に描く（表の図柄は最後。同じ位置の面は、あとに描いたほうが上に出る）
        for (SignMesh.Quad quad : quads) {
            TextureAtlasSprite sprite = quad.texture == SignMesh.Texture.PATTERN ? pattern
                    : quad.texture == SignMesh.Texture.BASE ? base : back;
            int quadLight = quad.front ? frontLight : light;
            for (int i = 0; i < 4; i++) {
                float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * quad.uvs[i * 2];
                float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * quad.uvs[i * 2 + 1];
                consumer.vertex(pose, quad.positions[i * 3], quad.positions[i * 3 + 1], quad.positions[i * 3 + 2])
                        .color(quad.shade, quad.shade, quad.shade, 1.0F)
                        .uv(u, v)
                        .uv2(quadLight)
                        .normal(normal, quad.nx, quad.ny, quad.nz)
                        .endVertex();
            }
        }
        poseStack.popPose();
    }

    /** 四角の並び。リソースを読み直したら（図柄の形が変わるので）全部作り直す。 */
    private List<SignMesh.Quad> mesh(ResourceLocation texture, BlockSafetySign block, boolean cutout, boolean backPattern) {
        int generation = SignTextures.generation();
        if (generation != meshGeneration) {
            meshes.clear();
            meshGeneration = generation;
        }
        return meshes.computeIfAbsent(new ShapeKey(texture, block, cutout, backPattern), key -> {
            // 下地（切り抜き OFF のとき図柄の下に貼る）。無地の看板は図柄そのものが下地なので重ねない
            boolean base = !cutout && !texture.equals(SignTextures.BLANK);
            SignOutline outline = cutout ? SignTextures.outlineOf(texture) : SQUARE;
            return SignMesh.build(outline, block.getLayout().getWidth(), block.getLayout().getHeight(), block.getFaceRow(),
                    block.getFaceZ(), base, backPattern);
        });
    }

    // 板そのものを TileEntity の描画で描くので、既定の 64 ブロックより遠くまで描く（256 ブロック。チャンクの描画距離 16 相当）。
    // これより遠い看板は、ポール付きの支柱と金具だけが見える
    @Override
    public int getViewDistance() {
        return 256;
    }

    /** 四角の並びのキー: テクスチャ・看板のブロック（種類ごとに 1 つなので、同じかどうかで比べる）・切り抜き・裏面。光るかは並びを変えないので含めない。 */
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
            return Objects.hash(texture, System.identityHashCode(block), cutout, backPattern);
        }
    }
}
