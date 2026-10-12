package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 壁掛け看板 1×1。壁に貼る薄い板（厚さ 1 ピクセル）で、向きは 4 方向。
 * 図柄 ID は TileEntity（{@link TileEntitySafetySign}）が持つ。ブロックモデルは無地の板だけで、
 * 図柄はクライアントの TileEntity の描画が前面に重ねる。
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
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        // アイテムの図柄を引き継ぐ（形の正しい ID だけ）
        String signId = SignNbt.getValidSignId(stack);
        TileEntity tile = world.getTileEntity(pos);
        if (signId != null && tile instanceof TileEntitySafetySign) {
            ((TileEntitySafetySign) tile).setSignId(signId);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        // 右クリックの分岐は doc/diagrams/right-click.md。
        // 看板アイテムを持っているときは GUI を開かない（そのまま隣に設置する）。スニーク中はバニラがここを呼ばず、
        // 看板アイテムなら ItemSafetySign.onItemUse が図柄を写す。
        // 両手で 2 回呼ばれるので、メインの手の 1 回だけで開く
        if (hand != EnumHand.MAIN_HAND || player.getHeldItemMainhand().getItem() instanceof ItemSafetySign) {
            return false;
        }
        if (world.isRemote) {
            // GUI はクライアントだけ（コンテナを持たないので、サーバーは何もしない）
            player.openGui(SafetySignsMod.instance, SafetySignsMod.GUI_SIGN_PICKER, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return createSignStack(world, pos);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // 図柄を保持したアイテム。プレイヤーが壊したときは harvestBlock まで TileEntity を残してあるので読める
        drops.add(createSignStack(world, pos));
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        // アイテムを落とすとき（クリエイティブ以外）は、ここではまだ消さない（getDrops で図柄を読むため）。harvestBlock で消す
        if (willHarvest) {
            return true;
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te,
            ItemStack tool) {
        super.harvestBlock(world, player, pos, state, te, tool);
        world.setBlockToAir(pos);
    }

    /** この看板の図柄を持ったアイテム（一覧にない ID もそのまま持つ）。 */
    private ItemStack createSignStack(IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        String signId = tile instanceof TileEntitySafetySign ? ((TileEntitySafetySign) tile).getSignId() : null;
        return ItemSafetySign.createStack(Item.getItemFromBlock(this), signId);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySafetySign();
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
