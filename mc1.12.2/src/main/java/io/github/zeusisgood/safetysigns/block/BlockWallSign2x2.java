package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/**
 * 壁掛け看板 2×2。マスごとの見た目は 1×1 と同じ薄い板で、どの部品か（{@link #PART}）を状態に持つ。
 * 部品の番号は {@link SignLayout}（0 左下が代表・1 右下・2 左上・3 右上。正面から見て）。
 * 板は、代表のマスの描画が図柄を 2×2 に引き伸ばし、図柄の形に切り抜いて描く（ほかのマスのモデルは何も描かない）。
 */
public class BlockWallSign2x2 extends BlockWallSign {

    public static final PropertyInteger PART = PropertyInteger.create("part", 0, 3);

    public BlockWallSign2x2() {
        super("wall_sign_2x2", SignLayout.WALL_2X2);
    }

    @Override
    public int getPart(IBlockState state) {
        return state.getValue(PART);
    }

    @Override
    public IBlockState withPart(IBlockState state, int part) {
        return state.withProperty(PART, part);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    // メタデータ: 下位 2 ビットが向き（horizontal index）、上位 2 ビットが部品
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(PART, (meta >> 2) & 3);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | state.getValue(PART) << 2;
    }
}
