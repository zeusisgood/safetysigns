package io.github.zeusisgood.safetysigns.block;

import com.mojang.serialization.MapCodec;
import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.GridPos;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignLayout;
import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 看板のブロックの共通部分（壁掛け 1×1・2×2、ポール付きで使い回す）。
 * <p>
 * 看板は {@link SignLayout} の並びで 1 マス以上を使う。図柄 ID は代表のマス（部品 0）の TileEntity だけが持ち、
 * どのマスを右クリック・ピックブロックしても代表のマスの図柄を使う。1 マスでも壊れたら全体を消し、
 * アイテムは壊れたマスから 1 個だけ落ちる（図: doc/diagrams/multiblock.md）。
 */
public abstract class BlockSafetySign extends Block implements EntityBlock {

    /** 看板の面が向いている方向（置いた人のほうを向く）。 */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final SignLayout layout;
    /** 看板の種類ごとに 1 つしかないブロック（設定を持たない）なので、読み書きはこのブロックそのもの。 */
    private final MapCodec<BlockSafetySign> codec = MapCodec.unit(this);

    protected BlockSafetySign(SignLayout layout) {
        // 素手でも壊せてアイテムが落ちる（道具を求めない）。ピストンでは動かない・壊れない
        // （代表のマス以外は TileEntity を持たないので、明示しないと押せてしまう）
        super(Properties.of().mapColor(MapColor.METAL).strength(1.0F, 5.0F).sound(SoundType.METAL).noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        this.layout = layout;
    }

    public SignLayout getLayout() {
        return layout;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    /** このマスの部品の番号（0 が代表）。1 マスの看板は常に 0。 */
    public int getPart(BlockState state) {
        return 0;
    }

    /** 部品の番号を変えた状態。1 マスの看板はそのまま。 */
    public BlockState withPart(BlockState state, int part) {
        return state;
    }

    // ---- 図柄を描く面（クライアントの描画と GUI のプレビューが使う） ----

    /** 図柄を描く面の下端の行（代表のマスから何マス上か）。面は幅いっぱいで、ここから看板の上端まで。 */
    public abstract int getFaceRow();

    /** 図柄を描く面（板の前面）の位置。北を向いた看板のモデルでの z（ブロック単位。前面は -z を向く）。 */
    public abstract double getFaceZ();

    /** 図柄を描く面の高さ（マス）。 */
    public int getFaceHeight() {
        return layout.getHeight() - getFaceRow();
    }

    // ---- マスの計算（common/SignLayout に任せる） ----

    /** このマスが属する看板の代表のマス。 */
    public BlockPos getOrigin(BlockState state, BlockPos pos) {
        return toBlockPos(layout.originOf(toGrid(pos), facingIndex(state), getPart(state)));
    }

    /** 代表のマスが origin のときの全部のマス（部品の番号の順）。 */
    public List<BlockPos> getCells(BlockState state, BlockPos origin) {
        List<BlockPos> cells = new ArrayList<>(layout.partCount());
        for (GridPos cell : layout.cells(toGrid(origin), facingIndex(state))) {
            cells.add(toBlockPos(cell));
        }
        return cells;
    }

    protected static int facingIndex(BlockState state) {
        return state.getValue(FACING).get2DDataValue();
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
    public static TileEntitySafetySign getSignTile(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockSafetySign)) {
            return null;
        }
        BlockSafetySign block = (BlockSafetySign) state.getBlock();
        BlockPos origin = block.getOrigin(state, pos);
        BlockState originState = world.getBlockState(origin);
        if (originState.getBlock() != block || block.getPart(originState) != 0
                || originState.getValue(FACING) != state.getValue(FACING)) {
            return null;
        }
        BlockEntity tile = world.getBlockEntity(origin);
        return tile instanceof TileEntitySafetySign ? (TileEntitySafetySign) tile : null;
    }

    // ---- 設置・右クリック ----

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 置いた人の向きに合わせる（看板の面が置いた人のほうを向く）。置く所は代表のマス（部品 0）。
        // ほかのマスは ItemSafetySign.placeBlock が置く
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // アイテムの見た目（図柄・切り抜き・裏面）を引き継ぐ（図柄 ID は形の正しいものだけ）。pos は代表のマス
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEntitySafetySign) {
            ((TileEntitySafetySign) tile).setAppearance(SignNbt.getValid(stack));
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        // 看板アイテムを持っているときは GUI を開かない（そのまま隣に設置する。ItemSafetySign.useOn）
        return stack.getItem() instanceof ItemSafetySign
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        // 右クリックの分岐は doc/diagrams/right-click.md。
        // スニーク中に物を持っているとバニラがここを呼ばず、看板アイテムなら ItemSafetySign.useOn が図柄を写す。
        // バニラはメインの手の 1 回だけここを呼ぶ
        TileEntitySafetySign tile = getSignTile(world, pos);
        if (tile == null) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide) {
            // GUI はクライアントだけ（コンテナを持たないので、サーバーは何もしない）。どのマスでも代表のマスを開く
            SafetySignsMod.proxy.openSignPicker(tile.getBlockPos());
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    // ---- 壊す・ピックブロック ----

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return createSignStack(world, pos);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // 図柄を保持したアイテム。壊れたマスの分だけ（全体で 1 個）。ほかのマスは onRemove が落とさずに消す。
        // プレイヤーが壊したときは playerDestroy まで消さずに残してあるので、代表のマスの TileEntity を読める
        // （爆発は、先に全部のマスのドロップを決めてから消すが、2 マス目からは消えたあとなので何も落ちない）
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(createSignStack(params.getLevel(), BlockPos.containing(origin)));
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos, Player player, boolean willHarvest,
            FluidState fluid) {
        // アイテムを落とすとき（クリエイティブ以外）は、ここではまだ消さない（getDrops で図柄を読むため）。playerDestroy で消す
        if (willHarvest) {
            playerWillDestroy(world, pos, state, player);
            return true;
        }
        return super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity tile,
            ItemStack tool) {
        super.playerDestroy(world, player, pos, state, tile, tool);
        world.removeBlock(pos, false);
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        // どう壊れても（プレイヤー・爆発・他 Mod の setBlock など）ここを通る。同じブロックの状態の変化（向きを変えるなど）は飛ばす。
        // 1 マスでも壊れたら、同じ看板のほかのマスを、アイテムを落とさずに消す
        // （消したマスの onRemove がまたここへ来るが、消えたマスと別の看板は飛ばすので止まる）
        if (!state.is(newState.getBlock()) && !world.isClientSide) {
            BlockPos origin = getOrigin(state, pos);
            for (BlockPos cell : getCells(state, origin)) {
                if (cell.equals(pos)) {
                    continue;
                }
                BlockState other = world.getBlockState(cell);
                if (other.getBlock() == this && other.getValue(FACING) == state.getValue(FACING)
                        && getOrigin(other, cell).equals(origin)) {
                    world.removeBlock(cell, false);
                }
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    /** この看板の見た目（図柄・切り抜き・裏面）を持ったアイテム（一覧にない ID もそのまま持つ）。代表のマスが欠けていれば既定。 */
    private ItemStack createSignStack(BlockGetter world, BlockPos pos) {
        TileEntitySafetySign tile = getSignTile(world, pos);
        return ItemSafetySign.createStack(asItem(), tile != null ? tile.getAppearance() : SignAppearance.DEFAULT);
    }

    // ---- TileEntity は代表のマスだけ ----

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return getPart(state) == 0 ? new TileEntitySafetySign(pos, state) : null;
    }

    // ---- 向き ----

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        // 鏡に映すと左右が入れ替わるので、部品の列も反転する
        BlockState mirrored = state.rotate(mirror.getRotation(state.getValue(FACING)));
        return mirror == Mirror.NONE ? mirrored : withPart(mirrored, layout.mirrorPart(getPart(state)));
    }
}
