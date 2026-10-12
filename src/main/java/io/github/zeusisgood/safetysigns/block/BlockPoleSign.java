package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * ポール付き看板。縦 3 マスで、下の 2 段（部品 0 が代表、部品 1）が支柱、最上段（部品 2）に 1×1 の看板（{@link SignLayout#POLE}）。
 * 支柱は 4 ピクセル角でマスの中央を通り、最上段では板の裏を通って上端の少し下で止まる。板（厚さ 1 ピクセル）は支柱の手前に付き、
 * 板と支柱の間に固定金具が 2 つある（支柱と金具はブロックモデル、板は図柄の形に切り抜いて SignRenderer が描く）。
 * 床（クリックした面が上面）にだけ置ける。図柄は代表のマス（最下段）の TileEntity が持つ。
 */
public class BlockPoleSign extends BlockSafetySign {

    public static final PropertyInteger PART = PropertyInteger.create("part", 0, 2);

    /** 看板の板がある部品（最上段）。 */
    private static final int PLATE_PART = 2;

    private static final double PX = 1.0 / 16.0;
    /** 支柱（4 ピクセル角、マスの中央）。 */
    private static final AxisAlignedBB POLE = new AxisAlignedBB(6 * PX, 0, 6 * PX, 10 * PX, 1, 10 * PX);
    // 最上段の板の外形（切り抜いた形ではなく四角）。金具の分だけ支柱から離れた手前（看板の面が向く側）に付く。
    // 添字は EnumFacing の horizontal index（S, W, N, E）
    private static final AxisAlignedBB[] PLATE = {
            new AxisAlignedBB(0, 0, 11 * PX, 1, 1, 12 * PX),  // 南を向く
            new AxisAlignedBB(4 * PX, 0, 0, 5 * PX, 1, 1),    // 西を向く
            new AxisAlignedBB(0, 0, 4 * PX, 1, 1, 5 * PX),    // 北を向く
            new AxisAlignedBB(11 * PX, 0, 0, 12 * PX, 1, 1),  // 東を向く
    };

    public BlockPoleSign() {
        super("pole_sign", SignLayout.POLE);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(PART, 0));
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
    public int getFaceRow() {
        // 最上段の板だけが面
        return PLATE_PART;
    }

    @Override
    public double getFaceZ() {
        // 北を向いたモデルの板（z = 4/16〜5/16）の前面
        return 4 * PX;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        // 床（クリックした面が上面）にだけ置ける。上の段のマスも同じ面で確かめる（ItemSafetySign.placeBlockAt）
        return side == EnumFacing.UP && super.canPlaceBlockOnSide(world, pos, side);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        // 当たり判定と選んだときの枠（既定でどちらもこれを使う）。支柱の段は細い柱、看板の段は板の外形の四角
        return getPart(state) == PLATE_PART ? PLATE[facingIndex(state)] : POLE;
    }

    // ---- 状態 ----

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    // メタデータ: 下位 2 ビットが向き（horizontal index）、上位 2 ビットが部品（0〜2。3 は使わない）
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(PART, Math.min((meta >> 2) & 3, PLATE_PART));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | state.getValue(PART) << 2;
    }
}
