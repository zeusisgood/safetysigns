package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 看板アイテムのモデル。無地のモデル（item/generated）を包み、コンポーネントの図柄 ID と切り抜きに合わせて差し替える。
 * 切り抜き ON は図柄のテクスチャ 1 枚（置いた看板と同じく、透明な所は板がない）、OFF は無地の下地 + 図柄の 2 枚重ね。
 * 図柄と切り抜きごとのモデルは初めて使うときに作って取っておく。手に持ったとき・GUI などの向きと大きさは、無地のモデルと同じ。
 */
public class SignItemModel extends BakedModelWrapper<BakedModel> {

    /** バニラの item/generated と同じ描き方（NeoForge の ItemLayerModel と同じ）。 */
    private static final RenderTypeGroup ITEM_RENDER_TYPES =
            new RenderTypeGroup(RenderType.translucent(), NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());

    private final Overrides overrides = new Overrides();

    public SignItemModel(BakedModel blankModel) {
        super(blankModel);
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    private final class Overrides extends ItemOverrides {
        // テクスチャと切り抜きごとのモデル。モデルを焼き直すとこのモデルごと作り直されるので、消す必要はない
        private final Map<Key, BakedModel> cache = new HashMap<>();

        @Override
        @Nullable
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity,
                int seed) {
            // 裏面の設定はアイコンに出さない（アイコンは表だけ）。無地は切り抜きの ON/OFF で見た目が変わらない
            SignAppearance appearance = SignNbt.get(stack);
            if (appearance.getSignId() == null) {
                return originalModel;
            }
            return cache.computeIfAbsent(new Key(SignTextures.textureOf(appearance.getSignId()), appearance.isCutout()), this::bake);
        }

        private BakedModel bake(Key key) {
            // 切り抜き ON は図柄だけ（透明な所はアイコンも抜ける）。OFF は無地の下地の上に図柄を重ねた四角
            List<ResourceLocation> textures = key.cutout
                    ? Arrays.asList(key.texture) : Arrays.asList(SignTextures.BLANK, key.texture);
            TextureAtlasSprite particle = SignTextures.sprite(key.texture);
            IModelBuilder<?> builder = IModelBuilder.of(originalModel.useAmbientOcclusion(), originalModel.usesBlockLight(),
                    originalModel.isGui3d(), originalModel.getTransforms(), ItemOverrides.EMPTY, particle, ITEM_RENDER_TYPES);
            for (int layer = 0; layer < textures.size(); layer++) {
                TextureAtlasSprite sprite = SignTextures.sprite(textures.get(layer));
                List<BakedQuad> quads = UnbakedGeometryHelper.bakeElements(
                        UnbakedGeometryHelper.createUnbakedItemElements(layer, sprite), material -> sprite, BlockModelRotation.X0_Y0);
                quads.forEach(builder::addUnculledFace);
            }
            return builder.build();
        }
    }

    private static final class Key {
        private final ResourceLocation texture;
        private final boolean cutout;

        Key(ResourceLocation texture, boolean cutout) {
            this.texture = texture;
            this.cutout = cutout;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && texture.equals(((Key) other).texture) && cutout == ((Key) other).cutout;
        }

        @Override
        public int hashCode() {
            return Objects.hash(texture, cutout);
        }
    }
}
