package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 壁掛け看板 2×2。マスごとの見た目は 1×1 と同じ薄い板で、どの部品か（{@link #PART}）を状態に持つ。
 * 部品の番号は {@link SignLayout}（0 左下が代表・1 右下・2 左上・3 右上。正面から見て）。
 * 板は、代表のマスの描画が図柄を 2×2 に引き伸ばし、図柄の形に切り抜いて描く（ほかのマスのモデルは何も描かない）。
 */
public class BlockWallSign2x2 extends BlockWallSign {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    public BlockWallSign2x2() {
        super(SignLayout.WALL_2X2);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    public int getPart(BlockState state) {
        return state.getValue(PART);
    }

    @Override
    public BlockState withPart(BlockState state, int part) {
        return state.setValue(PART, part);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
