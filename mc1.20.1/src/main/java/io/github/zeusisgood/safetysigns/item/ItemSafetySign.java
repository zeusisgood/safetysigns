package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 看板の設置用アイテム。図柄 ID をタグの直下（{@link SignNbt#KEY}）に持つ。
 * 図柄違いは NBT だけが違う同じアイテム（スタックしないのは仕様）。
 * スニークして設置済みの看板を右クリックすると、設置せずにその看板の図柄を写す（doc/diagrams/right-click.md）。
 * 複数マスの看板は、全部のマスに置けるときだけ置く（doc/diagrams/multiblock.md）。
 */
public class ItemSafetySign extends BlockItem {

    public ItemSafetySign(BlockSafetySign block) {
        super(block, new Item.Properties());
    }

    /** 見た目（図柄・切り抜き・裏面・光るか）を持ったアイテム（ドロップ・ピックブロック）。既定の見た目なら NBT なし。 */
    public static ItemStack createStack(Item item, SignAppearance appearance) {
        ItemStack stack = new ItemStack(item);
        SignNbt.set(stack, appearance);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // スニーク中に物を持っているとバニラが看板の use を呼ばずにここへ来る。看板なら図柄（切り抜き・裏面・光るかも）を写して終わり（設置しない）
        // 複数マスの看板は、どのマスを狙っても代表のマスの図柄
        Level world = context.getLevel();
        Player player = context.getPlayer();
        TileEntitySafetySign tile = BlockSafetySign.getSignTile(world, context.getClickedPos());
        if (player != null && player.isShiftKeyDown() && tile != null) {
            if (!world.isClientSide) {
                // 書き換えはサーバーだけ（手のアイテムはバニラの同期でクライアントへ届く）。
                // 種類・サイズは違ってよい。スタックはまとめて写す（図柄違いはもともとスタックしないため）。
                // 一覧にない ID もそのまま写す（設置するときに形を確かめる）
                SignNbt.set(context.getItemInHand(), tile.getAppearance());
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        // context の位置は代表のマス（BlockItem が置けるか確かめ済み）。ほかのマスも 1 つでも置けなければ失敗にする。
        // false を返すと BlockItem はアイテムを減らさない
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockSafetySign sign = (BlockSafetySign) getBlock();
        List<BlockPos> cells = sign.getCells(state, pos);
        boolean placeable = true;
        for (int part = 0; part < cells.size() && placeable; part++) {
            // 部品ごとに形が違う（ポール付きの支柱と板）ので、その部品の状態でぶつかるかを見る
            placeable = canPlacePart(context, cells.get(part), sign.withPart(state, part));
        }
        if (!placeable) {
            if (!world.isClientSide) {
                // クライアントは同じ判定で先に置いて見せることがある（サーバーにだけいる物とぶつかったなど）。
                // バニラが送り直すのはクリックした辺りだけなので、全部のマスの今の状態を送り直して幽霊ブロックを消す
                for (BlockPos cell : cells) {
                    BlockState current = world.getBlockState(cell);
                    world.sendBlockUpdated(cell, current, current, 3);
                }
            }
            return false;
        }
        // 代表のマス（TileEntity と図柄の引き継ぎは setPlacedBy）
        if (!super.placeBlock(context, state)) {
            return false;
        }
        for (int part = 1; part < cells.size(); part++) {
            if (!world.setBlock(cells.get(part), sign.withPart(state, part), 11)) {
                // 置けなかったら全体を消す（代表のマスを消せば onRemove が残りを消す）
                world.removeBlock(pos, false);
                return false;
            }
        }
        return true;
    }

    /** 看板の 1 マスを置けるか（置き換え可能・高さの範囲・いじってよい場所・人や物とぶつからない）。 */
    private static boolean canPlacePart(BlockPlaceContext context, BlockPos cell, BlockState state) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (world.isOutsideBuildHeight(cell)) {
            return false;
        }
        if (player != null && !player.mayUseItemAt(cell, context.getClickedFace(), context.getItemInHand())) {
            return false;
        }
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        return world.getBlockState(cell).canBeReplaced(BlockPlaceContext.at(context, cell, context.getClickedFace()))
                && world.isUnobstructed(state, cell, collision);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        // 図柄のコードと名前（一覧にない ID は「?」と ID）。既定と違う設定だけ 1 行ずつ足す。名前の一覧はクライアントだけが持つ
        SafetySignsMod.proxy.appendSignTooltip(SignNbt.get(stack), tooltip);
    }
}
