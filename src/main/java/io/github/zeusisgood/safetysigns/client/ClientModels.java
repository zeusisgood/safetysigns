package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** アイテムのモデルの登録（クライアントだけ）。 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID, value = Side.CLIENT)
public final class ClientModels {

    private ClientModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(SafetySignsBlocks.WALL_SIGN_1X1_ITEM, 0,
                new ModelResourceLocation(SafetySignsBlocks.WALL_SIGN_1X1_ITEM.getRegistryName(), "inventory"));
    }
}
