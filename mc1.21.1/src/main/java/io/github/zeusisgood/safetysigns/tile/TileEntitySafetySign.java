package io.github.zeusisgood.safetysigns.tile;

import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 看板の見た目（図柄 ID・切り抜き・裏面・光るか。{@link SignAppearance}）を持つ TileEntity。既定の値は NBT にキーを書かない。
 * サーバーは図柄の一覧を知らないので、ID の文字列と真偽値をそのまま保存・同期するだけ。
 * 複数マスの看板では、代表のマス（左下）だけが持つ。
 */
public class TileEntitySafetySign extends BlockEntity {

    private SignAppearance appearance = SignAppearance.DEFAULT;

    public TileEntitySafetySign(BlockPos pos, BlockState state) {
        super(SafetySignsBlocks.SIGN_TILE.get(), pos, state);
    }

    public SignAppearance getAppearance() {
        return appearance;
    }

    /** 見た目を変えて、保存とクライアントへの同期を予約する（サーバー側で呼ぶ）。 */
    public void setAppearance(SignAppearance appearance) {
        // 同じ値なら保存も同期もしない（連打で通信を増やさない）
        if (this.appearance.equals(appearance)) {
            return;
        }
        this.appearance = appearance;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
        super.loadAdditional(compound, registries);
        // 一覧にない ID も消さずに持つ（クライアントでは「?」で表示する）
        appearance = SignNbt.read(compound);
    }

    @Override
    protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
        super.saveAdditional(compound, registries);
        SignNbt.write(compound, appearance);
    }

    // クリエイティブの Ctrl + ピックブロックなどで付く BlockEntityTag を、OP 以外が置いたときは読ませない
    // （図柄 ID の形を確かめずに TileEntity へ入ってしまうため。図柄はアイテムの SignId から形を確かめて引き継ぐ）
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    // チャンクを送るときの同期
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    // sendBlockUpdated のあとの同期
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // 既定の見た目（無地に戻した）はタグが空になる。既定の onDataPacket は空のタグを読まないので、
    // 読まないと前の図柄が残る。空も既定の見た目として読む
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        appearance = SignNbt.read(packet.getTag());
    }
}
