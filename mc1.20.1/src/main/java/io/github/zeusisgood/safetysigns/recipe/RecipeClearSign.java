package io.github.zeusisgood.safetysigns.recipe;

import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 看板アイテム 1 個だけをクラフト枠に入れると、同じアイテムの無地（NBT なし。切り抜き・裏面・夜間に光るかも既定に戻る）になる。
 * 種類・サイズを問わず、看板アイテムならどれでもよい。もともと無地（NBT なし）なら何も出さない。
 * CustomRecipe なのでレシピ本には出ない。
 */
public class RecipeClearSign extends CustomRecipe {

    public RecipeClearSign(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level world) {
        ItemStack sign = findSingleSign(inv);
        return !sign.isEmpty() && sign.hasTag();
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registries) {
        ItemStack sign = findSingleSign(inv);
        return sign.isEmpty() ? ItemStack.EMPTY : new ItemStack(sign.getItem());
    }

    /** 枠にあるのが看板アイテム 1 マスだけならそれ、ほかに何かあれば空。 */
    private static ItemStack findSingleSign(CraftingContainer inv) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty() || !(stack.getItem() instanceof ItemSafetySign)) {
                return ItemStack.EMPTY;
            }
            found = stack;
        }
        return found;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SafetySignsRecipes.CLEAR_SIGN.get();
    }
}
