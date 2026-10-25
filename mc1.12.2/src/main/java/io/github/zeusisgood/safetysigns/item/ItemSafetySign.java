package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.client.SignTextures;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
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
 * 複数マスの看板は、全部のマスに置けるときだけ置く（doc/diagrams/multiblock.md）。
 */
public class ItemSafetySign extends ItemBlock {

    public ItemSafetySign(BlockSafetySign block) {
        super(block);
        setRegistryName(block.getRegistryName());
    }

    /** 見た目（図柄・切り抜き・裏面・光るか）を持ったアイテム（ドロップ・ピックブロック）。既定の見た目なら NBT なし。 */
    public static ItemStack createStack(Item item, SignAppearance appearance) {
        ItemStack stack = new ItemStack(item);
        SignNbt.set(stack, appearance);
        return stack;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        // スニーク中はバニラが看板の onBlockActivated を呼ばずにここへ来る。看板なら図柄（切り抜き・裏面・光るかも）を写して終わり（設置しない）
        // 複数マスの看板は、どのマスを狙っても代表のマスの図柄
        TileEntitySafetySign tile = BlockSafetySign.getSignTile(world, pos);
        if (player.isSneaking() && tile != null) {
            if (!world.isRemote) {
                // 書き換えはサーバーだけ（手のアイテムはバニラの同期でクライアントへ届く）。
                // 種類・サイズは違ってよい。スタックはまとめて写す（図柄違いはもともとスタックしないため）。
                // 一覧にない ID もそのまま写す（設置するときに形を確かめる）
                SignNbt.set(player.getHeldItem(hand), tile.getAppearance());
            }
            return EnumActionResult.SUCCESS;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, IBlockState newState) {
        // pos は代表のマス（ItemBlock が置けるか確かめ済み）。ほかのマスも 1 つでも置けなければ失敗にする。
        // false を返すと ItemBlock はアイテムを減らさない
        BlockSafetySign sign = (BlockSafetySign) block;
        if (!sign.getLayout().canPlace(BlockSafetySign.toGrid(pos), newState.getValue(BlockSafetySign.FACING).getHorizontalIndex(),
                cell -> canPlacePart(BlockSafetySign.toBlockPos(cell), stack, player, world, side))) {
            if (!world.isRemote) {
                // クライアントは同じ判定で先に置いて見せることがある（サーバーにだけいる物とぶつかったなど）。
                // バニラが送り直すのはクリックした辺りだけなので、全部のマスの今の状態を送り直して幽霊ブロックを消す
                for (BlockPos cell : sign.getCells(newState, pos)) {
                    IBlockState current = world.getBlockState(cell);
                    world.notifyBlockUpdate(cell, current, current, 3);
                }
            }
            return false;
        }
        // 代表のマス（TileEntity と図柄の引き継ぎは onBlockPlacedBy）
        if (!super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState)) {
            return false;
        }
        List<BlockPos> cells = sign.getCells(newState, pos);
        for (int part = 1; part < cells.size(); part++) {
            if (!world.setBlockState(cells.get(part), sign.withPart(newState, part), 11)) {
                // 置けなかったら全体を消す（代表のマスを消せば breakBlock が残りを消す）
                world.setBlockToAir(pos);
                return false;
            }
        }
        return true;
    }

    /** 看板の 1 マスを置けるか（置き換え可能・高さの範囲・いじってよい場所・置く人以外の人や物とぶつからない）。 */
    private boolean canPlacePart(BlockPos cell, ItemStack stack, EntityPlayer player, World world, EnumFacing side) {
        return !world.isOutsideBuildHeight(cell) && player.canPlayerEdit(cell, side, stack)
                && world.mayPlace(block, cell, false, side, player);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        // 図柄のコードと名前（一覧にない ID は「?」と ID）。既定と違う設定だけ 1 行ずつ足す
        SignAppearance appearance = SignNbt.get(stack);
        tooltip.add(SignTextures.describe(appearance.getSignId()));
        // 無地は常に切り抜き ON なので、出るのは図柄のある看板だけ
        if (!appearance.isCutout()) {
            tooltip.add(I18n.format("tooltip.safetysigns.no_cutout"));
        }
        if (appearance.isBackPattern()) {
            tooltip.add(I18n.format("tooltip.safetysigns.double_sided"));
        }
        if (!appearance.isGlow()) {
            tooltip.add(I18n.format("tooltip.safetysigns.no_glow"));
        }
    }
}
