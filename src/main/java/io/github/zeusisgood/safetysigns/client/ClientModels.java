package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
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

    private static final ModelResourceLocation WALL_SIGN_1X1_INVENTORY =
            new ModelResourceLocation(SafetySignsBlocks.WALL_SIGN_1X1_ITEM.getRegistryName(), "inventory");

    private ClientModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(SafetySignsBlocks.WALL_SIGN_1X1_ITEM, 0, WALL_SIGN_1X1_INVENTORY);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySafetySign.class, new SignRenderer());
    }

    // 無地のアイテムモデルを、NBT の図柄で差し替えるモデルで包む
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        IBakedModel blank = event.getModelRegistry().getObject(WALL_SIGN_1X1_INVENTORY);
        if (blank != null) {
            event.getModelRegistry().putObject(WALL_SIGN_1X1_INVENTORY, new SignItemModel(blank));
        }
    }
}
