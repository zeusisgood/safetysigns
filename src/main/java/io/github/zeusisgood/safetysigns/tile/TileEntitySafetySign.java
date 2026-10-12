package io.github.zeusisgood.safetysigns.tile;

import io.github.zeusisgood.safetysigns.item.SignNbt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;

/**
 * 看板の図柄 ID を持つ TileEntity。図柄がなければ「無地」（NBT にキーを書かない）。
 * サーバーは図柄の一覧を知らないので、ID の文字列をそのまま保存・同期するだけ。
 */
public class TileEntitySafetySign extends TileEntity {

    @Nullable
    private String signId;

    @Nullable
    public String getSignId() {
        return signId;
    }

    /** 図柄を変えて、保存とクライアントへの同期を予約する（サーバー側で呼ぶ）。null は無地。 */
    public void setSignId(@Nullable String signId) {
        this.signId = signId;
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        // 一覧にない ID も消さずに持つ（クライアントでは「?」で表示する）
        signId = SignNbt.readSignId(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        SignNbt.writeSignId(compound, signId);
        return compound;
    }

    // チャンクを送るときの同期
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    // notifyBlockUpdate のあとの同期
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
