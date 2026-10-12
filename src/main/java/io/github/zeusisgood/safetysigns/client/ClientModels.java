package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** アイテムのモデルと、看板の図柄の描画の登録（クライアントだけ）。 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID, value = Side.CLIENT)
public final class ClientModels {

    private ClientModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (Item item : SafetySignsBlocks.SIGN_ITEMS) {
            ModelLoader.setCustomModelResourceLocation(item, 0, inventoryModel(item));
        }
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySafetySign.class, new SignRenderer());
    }

    // 無地のアイテムモデルを、NBT の図柄で差し替えるモデルで包む
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        for (Item item : SafetySignsBlocks.SIGN_ITEMS) {
            ModelResourceLocation location = inventoryModel(item);
            IBakedModel blank = event.getModelRegistry().getObject(location);
            if (blank != null) {
                event.getModelRegistry().putObject(location, new SignItemModel(blank));
            }
        }
    }

    private static ModelResourceLocation inventoryModel(Item item) {
        return new ModelResourceLocation(item.getRegistryName(), "inventory");
    }
}
