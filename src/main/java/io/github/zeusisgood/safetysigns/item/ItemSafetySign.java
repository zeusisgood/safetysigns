package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.client.SignTextures;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 看板の設置用アイテム。図柄 ID をタグの直下（{@link SignNbt#KEY}）に持つ。
 * 図柄違いは NBT だけが違う同じアイテム（スタックしないのは仕様）。
 * スニークして設置済みの看板を右クリックすると、設置せずにその看板の図柄を写す（doc/diagrams/right-click.md）。
 */
public class ItemSafetySign extends ItemBlock {

    public ItemSafetySign(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
    }

    /** 図柄入りのアイテム（ドロップ・ピックブロック・クラフトの結果）。null は無地（NBT なし）。 */
    public static ItemStack createStack(Item item, @Nullable String signId) {
        ItemStack stack = new ItemStack(item);
        SignNbt.setSignId(stack, signId);
        return stack;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        // スニーク中はバニラが看板の onBlockActivated を呼ばずにここへ来る。看板なら図柄を写して終わり（設置しない）
        TileEntity tile = world.getTileEntity(pos);
        if (player.isSneaking() && tile instanceof TileEntitySafetySign) {
            if (!world.isRemote) {
                // 書き換えはサーバーだけ（手のアイテムはバニラの同期でクライアントへ届く）。
                // 種類・サイズは違ってよい。スタックはまとめて写す（図柄違いはもともとスタックしないため）。
                // 一覧にない ID もそのまま写す（設置するときに形を確かめる）
                SignNbt.setSignId(player.getHeldItem(hand), ((TileEntitySafetySign) tile).getSignId());
            }
            return EnumActionResult.SUCCESS;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        // 図柄のコードと名前（一覧にない ID は「?」と ID）
        tooltip.add(SignTextures.describe(SignNbt.getSignId(stack)));
    }
}
