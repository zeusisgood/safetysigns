package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Map;

/** アイテムのモデルと、看板の図柄の描画・定義ファイルの読み込みの登録（クライアントだけ）。 */
public final class ClientModels {

    private ClientModels() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(ClientModels::registerRenderers);
        modBus.addListener(ClientModels::onModelBake);
        modBus.addListener(ClientModels::registerReloadListeners);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SafetySignsBlocks.SIGN_TILE.get(), SignRenderer::new);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SignTextures.INSTANCE);
    }

    // 無地のアイテムモデルを、NBT の図柄で差し替えるモデルで包む
    private static void onModelBake(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (DeferredHolder<Item, Item> item : SafetySignsBlocks.SIGN_ITEMS) {
            ModelResourceLocation location = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item.get()));
            BakedModel blank = models.get(location);
            if (blank != null) {
                models.put(location, new SignItemModel(blank));
            }
        }
    }
}
