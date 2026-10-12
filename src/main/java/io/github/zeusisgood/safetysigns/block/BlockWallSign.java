package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * 壁掛け看板 1×1。壁に貼る薄い板（厚さ 1 ピクセル）で、向きは 4 方向。
 * 図柄 ID は TileEntity（{@link TileEntitySafetySign}）が持つ。ブロックモデルは無地の板だけで、
 * 図柄はクライアントの TileEntity の描画が前面に重ねる。設置・右クリック・壊すときの処理は {@link BlockSafetySign}。
 */
public class BlockWallSign extends BlockSafetySign {

    private static final double THICKNESS = 1.0 / 16.0;
    // 添字は EnumFacing の horizontal index（S, W, N, E）
    private static final AxisAlignedBB[] BOUNDS = {
            new AxisAlignedBB(0, 0, 0, 1, 1, THICKNESS),      // 南を向く → 北の壁に貼る
            new AxisAlignedBB(1 - THICKNESS, 0, 0, 1, 1, 1),  // 西を向く → 東の壁に貼る
            new AxisAlignedBB(0, 0, 1 - THICKNESS, 1, 1, 1),  // 北を向く → 南の壁に貼る
            new AxisAlignedBB(0, 0, 0, THICKNESS, 1, 1),      // 東を向く → 西の壁に貼る
    };

    public BlockWallSign() {
        this("wall_sign_1x1", SignLayout.SINGLE);
    }

    protected BlockWallSign(String name, SignLayout layout) {
        super(name, layout);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public int getFaceRow() {
        // 看板全体が面
        return 0;
    }

    @Override
    public double getFaceZ() {
        // 壁に貼る板（モデルの z = 15/16〜1）の前面
        return 1.0 - THICKNESS;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        // 複数マスの看板も、マスごとに同じ薄い板
        return BOUNDS[state.getValue(FACING).getHorizontalIndex()];
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }
}
