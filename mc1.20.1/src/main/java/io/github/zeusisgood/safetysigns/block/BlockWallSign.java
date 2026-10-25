package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 壁掛け看板 1×1。壁に貼る薄い板（厚さ 1 ピクセル）で、向きは 4 方向。
 * 図柄 ID は TileEntity（{@link TileEntitySafetySign}）が持つ。ブロックモデルは何も描かず（壊すときの粒だけ）、
 * 板はクライアントの TileEntity の描画が図柄の形に切り抜いて描く。当たり判定は切り抜いた形ではなく板の外形の四角。設置・右クリック・壊すときの処理は {@link BlockSafetySign}。
 */
public class BlockWallSign extends BlockSafetySign {

    private static final double THICKNESS = 1.0;
    // 添字は Direction の 2D data value（S, W, N, E）。単位はピクセル
    private static final VoxelShape[] BOUNDS = {
            Block.box(0, 0, 0, 16, 16, THICKNESS),       // 南を向く → 北の壁に貼る
            Block.box(16 - THICKNESS, 0, 0, 16, 16, 16), // 西を向く → 東の壁に貼る
            Block.box(0, 0, 16 - THICKNESS, 16, 16, 16), // 北を向く → 南の壁に貼る
            Block.box(0, 0, 0, THICKNESS, 16, 16),       // 東を向く → 西の壁に貼る
    };

    public BlockWallSign() {
        this(SignLayout.SINGLE);
    }

    protected BlockWallSign(SignLayout layout) {
        super(layout);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public int getFaceRow() {
        // 看板全体が面
        return 0;
    }

    @Override
    public double getFaceZ() {
        // 壁に貼る板（モデルの z = 15/16〜1）の前面
        return 1.0 - THICKNESS / 16.0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // 複数マスの看板も、マスごとに同じ薄い板
        return BOUNDS[facingIndex(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
