package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.client.SignTextures;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 看板の設置用アイテム。図柄 ID をタグの直下（{@link SignNbt#KEY}）に持つ。
 * 図柄違いは NBT だけが違う同じアイテム（スタックしないのは仕様）。
 */
public class ItemSafetySign extends ItemBlock {

    public ItemSafetySign(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        // 図柄のコードと名前（一覧にない ID は「?」と ID）
        tooltip.add(SignTextures.describe(SignNbt.getSignId(stack)));
    }
}
