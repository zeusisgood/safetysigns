package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * 壁掛け看板 1×1。壁に貼る薄い板（厚さ 1 ピクセル）で、向きは 4 方向。
 * 0.1.0 の時点では図柄を w003 に固定している（見た目はブロックモデルのテクスチャだけ）。
 * 図柄 ID を NBT に持つのは仕様書 9 の 2 から。
 */
public class BlockWallSign extends Block {

    /** 看板の面が向いている方向（置いた人のほうを向く）。板はその反対側（壁の側）に寄せる。 */
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    /** 素手でも壊せてアイテムが落ちるように、道具のいらない金属の材質にする（Material.IRON はつるはしが必要）。 */
    private static final Material SIGN_MATERIAL = new Material(MapColor.IRON);

    private static final double THICKNESS = 1.0 / 16.0;
    // 添字は EnumFacing の horizontal index（S, W, N, E）
    private static final AxisAlignedBB[] BOUNDS = {
            new AxisAlignedBB(0, 0, 0, 1, 1, THICKNESS),      // 南を向く → 北の壁に貼る
            new AxisAlignedBB(1 - THICKNESS, 0, 0, 1, 1, 1),  // 西を向く → 東の壁に貼る
            new AxisAlignedBB(0, 0, 1 - THICKNESS, 1, 1, 1),  // 北を向く → 南の壁に貼る
            new AxisAlignedBB(0, 0, 0, THICKNESS, 1, 1),      // 東を向く → 西の壁に貼る
    };

    public BlockWallSign() {
        super(SIGN_MATERIAL);
        setRegistryName(SafetySignsMod.MODID, "wall_sign_1x1");
        setUnlocalizedName(SafetySignsMod.MODID + ".wall_sign_1x1");
        setCreativeTab(CreativeTabs.DECORATIONS);
        setHardness(1.0F);
        setResistance(5.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
            int meta, EntityLivingBase placer) {
        // 置いた人の向きに合わせる（看板の面が置いた人のほうを向く）
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOUNDS[state.getValue(FACING).getHorizontalIndex()];
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        // 薄い板なので、隣のブロックの面を隠さない・フェンスなどをつながせない
        return BlockFaceShape.UNDEFINED;
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

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }
}
