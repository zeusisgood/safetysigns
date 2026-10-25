package io.github.zeusisgood.safetysigns.recipe;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * コードで作るレシピの登録。通常のレシピ（材料から作る）は JSON（data/safetysigns/recipes/）で足す。
 * 無地に戻すレシピも、種類の登録はここ、使うことは JSON（clear_sign.json）で決める。
 */
public final class SafetySignsRecipes {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SafetySignsMod.MODID);

    public static final RegistryObject<RecipeSerializer<RecipeClearSign>> CLEAR_SIGN =
            SERIALIZERS.register("clear_sign", () -> new SimpleCraftingRecipeSerializer<>(RecipeClearSign::new));

    private SafetySignsRecipes() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
