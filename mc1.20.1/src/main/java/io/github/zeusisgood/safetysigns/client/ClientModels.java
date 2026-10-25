package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (RegistryObject<Item> item : SafetySignsBlocks.SIGN_ITEMS) {
            ResourceLocation location = new ModelResourceLocation(ForgeRegistries.ITEMS.getKey(item.get()), "inventory");
            BakedModel blank = models.get(location);
            if (blank != null) {
                models.put(location, new SignItemModel(blank));
            }
        }
    }
}
