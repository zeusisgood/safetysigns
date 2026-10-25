package io.github.zeusisgood.safetysigns.recipe;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * コードで作るレシピの登録。通常のレシピ（材料から作る）は JSON（data/safetysigns/recipe/）で足す。
 * 無地に戻すレシピも、種類の登録はここ、使うことは JSON（clear_sign.json）で決める。
 */
public final class SafetySignsRecipes {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SafetySignsMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RecipeClearSign>> CLEAR_SIGN =
            SERIALIZERS.register("clear_sign", () -> new SimpleCraftingRecipeSerializer<>(RecipeClearSign::new));

    private SafetySignsRecipes() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
