package io.github.zeusisgood.safetysigns;

import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.client.gui.GuiSignPicker;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/** GUI の番号と画面の対応。図柄選択の GUI はコンテナを持たないので、サーバー側は何も開かない。 */
public class SafetySignsGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == SafetySignsMod.GUI_SIGN_PICKER) {
            // pos は代表のマス（BlockSafetySign.onBlockActivated がたどって開く）
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState state = world.getBlockState(pos);
            TileEntity tile = world.getTileEntity(pos);
            if (state.getBlock() instanceof BlockSafetySign && tile instanceof TileEntitySafetySign) {
                // プレビューは図柄を描く面の縦横比（ポール付きは支柱を除いた最上段の 1×1）
                BlockSafetySign block = (BlockSafetySign) state.getBlock();
                return new GuiSignPicker(pos, ((TileEntitySafetySign) tile).getAppearance(), block.getLayout().getWidth(),
                        block.getFaceHeight());
            }
        }
        return null;
    }
}
