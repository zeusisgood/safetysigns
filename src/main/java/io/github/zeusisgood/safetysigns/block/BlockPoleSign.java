package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.common.SignLayout;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ポール付き看板。縦 2 マスで、下段（部品 0、代表）が支柱、上段（部品 1）が 1×1 の看板（{@link SignLayout#POLE}）。
 * 支柱は 4 ピクセル角でマスの中央を通り、上段では板の裏まで伸びる。板（厚さ 1 ピクセル）は支柱の前に付く。
 * 床（クリックした面が上面）にだけ置ける。図柄は代表のマス（下段）の TileEntity が持ち、描画は上段の板の前面に重ねる。
 */
public class BlockPoleSign extends BlockSafetySign {

    public static final PropertyInteger PART = PropertyInteger.create("part", 0, 1);

    private static final double PX = 1.0 / 16.0;
    /** 支柱（4 ピクセル角、マスの中央）。 */
    private static final AxisAlignedBB POLE = new AxisAlignedBB(6 * PX, 0, 6 * PX, 10 * PX, 1, 10 * PX);
    // 上段の板。支柱の前（看板の面が向く側）に付く。添字は EnumFacing の horizontal index（S, W, N, E）
    private static final AxisAlignedBB[] PLATE = {
            new AxisAlignedBB(0, 0, 10 * PX, 1, 1, 11 * PX),  // 南を向く
            new AxisAlignedBB(5 * PX, 0, 0, 6 * PX, 1, 1),    // 西を向く
            new AxisAlignedBB(0, 0, 5 * PX, 1, 1, 6 * PX),    // 北を向く
            new AxisAlignedBB(10 * PX, 0, 0, 11 * PX, 1, 1),  // 東を向く
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
        // 上段の板だけが面
        return 1;
    }

    @Override
    public double getFaceZ() {
        // 北を向いたモデルの板（z = 5/16〜6/16）の前面
        return 5 * PX;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        // 床（クリックした面が上面）にだけ置ける。上段のマスも同じ面で確かめる（ItemSafetySign.placeBlockAt）
        return side == EnumFacing.UP && super.canPlaceBlockOnSide(world, pos, side);
    }

    // ---- 当たり判定（見た目に合わせる: 支柱は細く、板は薄く） ----

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        // 選んだときの枠。上段は板（裏の支柱は collisionRayTrace で別に当てる）
        return getPart(state) == 0 ? POLE : PLATE[facingIndex(state)];
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, collidingBoxes, POLE);
        if (getPart(state) == 1) {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, PLATE[facingIndex(state)]);
        }
    }

    @Override
    @Nullable
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        // 上段は板と、板の裏の支柱の両方に当てる（裏から支柱を狙っても選べるように）。近いほう
        RayTraceResult pole = rayTrace(pos, start, end, POLE);
        if (getPart(state) == 0) {
            return pole;
        }
        RayTraceResult plate = rayTrace(pos, start, end, PLATE[facingIndex(state)]);
        if (pole == null) {
            return plate;
        }
        if (plate == null) {
            return pole;
        }
        return plate.hitVec.squareDistanceTo(start) <= pole.hitVec.squareDistanceTo(start) ? plate : pole;
    }

    // ---- 状態 ----

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    // メタデータ: 下位 2 ビットが向き（horizontal index）、ビット 2 が部品
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)).withProperty(PART, (meta >> 2) & 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | state.getValue(PART) << 2;
    }
}
