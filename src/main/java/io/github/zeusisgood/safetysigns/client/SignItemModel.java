package io.github.zeusisgood.safetysigns.client;

import com.google.common.collect.ImmutableList;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 看板アイテムのモデル。無地のモデル（item/generated）を包み、NBT の図柄 ID に合わせて
 * 「無地の板 + 図柄」の 2 枚重ねのモデルに差し替える。図柄ごとのモデルは初めて使うときに作って取っておく。
 */
public class SignItemModel extends BakedModelWrapper<IBakedModel> {

    private final Overrides overrides = new Overrides();

    public SignItemModel(IBakedModel blankModel) {
        super(blankModel);
    }

    @Override
    public ItemOverrideList getOverrides() {
        return overrides;
    }

    private final class Overrides extends ItemOverrideList {
        // テクスチャごとのモデル。テクスチャを貼り直すとこのモデルごと作り直されるので、消す必要はない
        private final Map<ResourceLocation, IBakedModel> cache = new HashMap<>();

        Overrides() {
            super(Collections.emptyList());
        }

        @Override
        public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, @Nullable World world,
                @Nullable EntityLivingBase entity) {
            String signId = SignNbt.getSignId(stack);
            if (signId == null) {
                return originalModel;
            }
            return cache.computeIfAbsent(SignTextures.textureOf(signId), this::bake);
        }

        private IBakedModel bake(ResourceLocation texture) {
            IBakedModel layers = new ItemLayerModel(ImmutableList.of(SignTextures.BLANK, texture))
                    .bake(TRSRTransformation.identity(), DefaultVertexFormats.ITEM, SignTextures::sprite);
            return new WithBlankTransforms(layers, SignItemModel.this.originalModel);
        }
    }

    /** 手に持ったとき・GUI などの向きと大きさは、無地のモデル（item/generated）と同じにする。 */
    private static final class WithBlankTransforms extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel blankModel;

        WithBlankTransforms(IBakedModel layers, IBakedModel blankModel) {
            super(layers);
            this.blankModel = blankModel;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            return Pair.of(this, blankModel.handlePerspective(type).getRight());
        }
    }
}
