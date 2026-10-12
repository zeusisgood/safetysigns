package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.GridPos;
import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 看板のブロックの共通部分（1×1・2×2・ポール付きで使い回す）。
 * <p>
 * 看板は {@link SignLayout} の並びで 1 マス以上を使う。図柄 ID は代表のマス（部品 0）の TileEntity だけが持ち、
 * どのマスを右クリック・ピックブロックしても代表のマスの図柄を使う。1 マスでも壊れたら全体を消し、
 * アイテムは壊れたマスから 1 個だけ落ちる（図: doc/diagrams/multiblock.md）。
 */
public abstract class BlockSafetySign extends Block {

    /** 看板の面が向いている方向（置いた人のほうを向く）。 */
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    /** 素手でも壊せてアイテムが落ちるように、道具のいらない金属の材質にする（Material.IRON はつるはしが必要）。 */
    private static final Material SIGN_MATERIAL = new Material(MapColor.IRON);

    private final SignLayout layout;

    protected BlockSafetySign(String name, SignLayout layout) {
        super(SIGN_MATERIAL);
        this.layout = layout;
        setRegistryName(SafetySignsMod.MODID, name);
        setUnlocalizedName(SafetySignsMod.MODID + "." + name);
        setCreativeTab(CreativeTabs.DECORATIONS);
        setHardness(1.0F);
        setResistance(5.0F);
        setSoundType(SoundType.METAL);
    }

    public SignLayout getLayout() {
        return layout;
    }

    /** このマスの部品の番号（0 が代表）。1 マスの看板は常に 0。 */
    public int getPart(IBlockState state) {
        return 0;
    }

    /** 部品の番号を変えた状態。1 マスの看板はそのまま。 */
    public IBlockState withPart(IBlockState state, int part) {
        return state;
    }

    // ---- マスの計算（common/SignLayout に任せる） ----

    /** このマスが属する看板の代表のマス。 */
    public BlockPos getOrigin(IBlockState state, BlockPos pos) {
        return toBlockPos(layout.originOf(toGrid(pos), facingIndex(state), getPart(state)));
    }

    /** 代表のマスが origin のときの全部のマス（部品の番号の順）。 */
    public List<BlockPos> getCells(IBlockState state, BlockPos origin) {
        List<BlockPos> cells = new ArrayList<>(layout.partCount());
        for (GridPos cell : layout.cells(toGrid(origin), facingIndex(state))) {
            cells.add(toBlockPos(cell));
        }
        return cells;
    }

    protected static int facingIndex(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    public static GridPos toGrid(BlockPos pos) {
        return new GridPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toBlockPos(GridPos pos) {
        return new BlockPos(pos.x, pos.y, pos.z);
    }

    /**
     * どのマスからでも、看板の図柄を持つ代表のマスの TileEntity をたどる。
     * 看板でない・代表のマスが欠けている（壊れかけ）なら null。
     */
    @Nullable
    public static TileEntitySafetySign getSignTile(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockSafetySign)) {
            return null;
        }
        BlockSafetySign block = (BlockSafetySign) state.getBlock();
        BlockPos origin = block.getOrigin(state, pos);
        IBlockState originState = world.getBlockState(origin);
        if (originState.getBlock() != block || block.getPart(originState) != 0
                || originState.getValue(FACING) != state.getValue(FACING)) {
            return null;
        }
        TileEntity tile = world.getTileEntity(origin);
        return tile instanceof TileEntitySafetySign ? (TileEntitySafetySign) tile : null;
    }

    // ---- 設置・右クリック ----

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
            int meta, EntityLivingBase placer) {
        // 置いた人の向きに合わせる（看板の面が置いた人のほうを向く）。置く所は代表のマス（部品 0）。
        // ほかのマスは ItemSafetySign.placeBlockAt が置く
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        // アイテムの図柄を引き継ぐ（形の正しい ID だけ）。pos は代表のマス
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
        TileEntitySafetySign tile = getSignTile(world, pos);
        if (tile == null) {
            return false;
        }
        if (world.isRemote) {
            // GUI はクライアントだけ（コンテナを持たないので、サーバーは何もしない）。どのマスでも代表のマスを開く
            BlockPos origin = tile.getPos();
            player.openGui(SafetySignsMod.instance, SafetySignsMod.GUI_SIGN_PICKER, world, origin.getX(), origin.getY(), origin.getZ());
        }
        return true;
    }

    // ---- 壊す・ピックブロック ----

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return createSignStack(world, pos);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // 図柄を保持したアイテム。壊れたマスの分だけ（全体で 1 個）。ほかのマスは breakBlock が落とさずに消す。
        // プレイヤーが壊したときは harvestBlock まで消さずに残してあるので、代表のマスの TileEntity を読める
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

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // どう壊れても（プレイヤー・爆発・他 Mod の setBlockState など）ここを通る（サーバーだけ）。
        // この時点で pos はもう置き換わっている。1 マスでも壊れたら、同じ看板のほかのマスを、アイテムを落とさずに消す
        // （消したマスの breakBlock がまたここへ来るが、消えたマスと別の看板は飛ばすので止まる）
        super.breakBlock(world, pos, state);
        BlockPos origin = getOrigin(state, pos);
        for (BlockPos cell : getCells(state, origin)) {
            if (cell.equals(pos)) {
                continue;
            }
            IBlockState other = world.getBlockState(cell);
            if (other.getBlock() == this && other.getValue(FACING) == state.getValue(FACING)
                    && getOrigin(other, cell).equals(origin)) {
                world.setBlockToAir(cell);
            }
        }
    }

    @Override
    public EnumPushReaction getMobilityFlag(IBlockState state) {
        // ピストンでは動かない・壊れない（代表のマス以外は TileEntity を持たないので、明示しないと押せてしまう）
        return EnumPushReaction.BLOCK;
    }

    /** この看板の図柄を持ったアイテム（一覧にない ID もそのまま持つ）。代表のマスが欠けていれば無地。 */
    private ItemStack createSignStack(IBlockAccess world, BlockPos pos) {
        TileEntitySafetySign tile = getSignTile(world, pos);
        return ItemSafetySign.createStack(Item.getItemFromBlock(this), tile != null ? tile.getSignId() : null);
    }

    // ---- TileEntity は代表のマスだけ ----

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return getPart(state) == 0;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySafetySign();
    }

    // ---- 形 ----

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
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        // 鏡に映すと左右が入れ替わるので、部品の列も反転する
        IBlockState mirrored = state.withRotation(mirror.toRotation(state.getValue(FACING)));
        return mirror == Mirror.NONE ? mirrored : withPart(mirrored, layout.mirrorPart(getPart(state)));
    }
}
