package io.github.zeusisgood.safetysigns.recipe;

import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * 看板アイテム 1 個だけをクラフト枠に入れると、同じアイテムの無地（NBT なし）になる。
 * 種類・サイズを問わず、看板アイテムならどれでもよい。もともと無地（NBT なし）なら何も出さない。
 */
public class RecipeClearSign extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack sign = findSingleSign(inv);
        return !sign.isEmpty() && sign.hasTagCompound();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack sign = findSingleSign(inv);
        return sign.isEmpty() ? ItemStack.EMPTY : new ItemStack(sign.getItem());
    }

    /** 枠にあるのが看板アイテム 1 マスだけならそれ、ほかに何かあれば空。 */
    private static ItemStack findSingleSign(InventoryCrafting inv) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
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
    public boolean canFit(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        // 結果は入れた看板で決まるので、決まった出力はない
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isDynamic() {
        // レシピ本に出さない
        return true;
    }
}
