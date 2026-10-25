package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * ポール付き看板。縦 3 マスで、下の 2 段（部品 0 が代表、部品 1）が支柱、最上段（部品 2）に 1×1 の看板（{@link SignLayout#POLE}）。
 * 支柱は 4 ピクセル角でマスの中央を通り、最上段では板の裏を通って上端の少し下で止まる。板（厚さ 1 ピクセル）は支柱の手前に付き、
 * 板と支柱の間に固定金具が 2 つある（支柱と金具はブロックモデル、板は図柄の形に切り抜いて SignRenderer が描く）。
 * 床（クリックした面が上面）にだけ置ける。図柄は代表のマス（最下段）の TileEntity が持つ。
 */
public class BlockPoleSign extends BlockSafetySign {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

    /** 看板の板がある部品（最上段）。 */
    private static final int PLATE_PART = 2;

    /** 支柱（4 ピクセル角、マスの中央）。 */
    private static final VoxelShape POLE = Block.box(6, 0, 6, 10, 16, 10);
    // 最上段の板の外形（切り抜いた形ではなく四角）。金具の分だけ支柱から離れた手前（看板の面が向く側）に付く。
    // 添字は Direction の 2D data value（S, W, N, E）
    private static final VoxelShape[] PLATE = {
            Block.box(0, 0, 11, 16, 16, 12),  // 南を向く
            Block.box(4, 0, 0, 5, 16, 16),    // 西を向く
            Block.box(0, 0, 4, 16, 16, 5),    // 北を向く
            Block.box(11, 0, 0, 12, 16, 16),  // 東を向く
    };

    public BlockPoleSign() {
        super(SignLayout.POLE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
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
    public int getFaceRow() {
        // 最上段の板だけが面
        return PLATE_PART;
    }

    @Override
    public double getFaceZ() {
        // 北を向いたモデルの板（z = 4/16〜5/16）の前面
        return 4.0 / 16.0;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 床（クリックした面が上面）にだけ置ける
        return context.getClickedFace() == Direction.UP ? super.getStateForPlacement(context) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // 当たり判定と選んだときの枠（既定でどちらもこれを使う）。支柱の段は細い柱、看板の段は板の外形の四角
        return getPart(state) == PLATE_PART ? PLATE[facingIndex(state)] : POLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
