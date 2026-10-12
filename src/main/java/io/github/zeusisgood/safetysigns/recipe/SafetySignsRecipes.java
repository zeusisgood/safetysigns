package io.github.zeusisgood.safetysigns.recipe;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * コードで作るレシピの登録。通常のレシピ（材料から作る）は JSON で足す（仕様書「9. 作業の順番」の 8）。
 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID)
public final class SafetySignsRecipes {

    private SafetySignsRecipes() {
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        event.getRegistry().register(new RecipeClearSign().setRegistryName(SafetySignsMod.MODID, "clear_sign"));
    }
}
