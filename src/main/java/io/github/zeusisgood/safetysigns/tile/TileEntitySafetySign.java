package io.github.zeusisgood.safetysigns.tile;

import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.item.SignNbt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 看板の見た目（図柄 ID・切り抜き・裏面。{@link SignAppearance}）を持つ TileEntity。既定の値は NBT にキーを書かない。
 * サーバーは図柄の一覧を知らないので、ID の文字列と真偽値をそのまま保存・同期するだけ。
 * 複数マスの看板では、代表のマス（左下）だけが持つ。
 */
public class TileEntitySafetySign extends TileEntity {

    private SignAppearance appearance = SignAppearance.DEFAULT;

    public SignAppearance getAppearance() {
        return appearance;
    }

    /** 図柄 ID。null は無地。 */
    @Nullable
    public String getSignId() {
        return appearance.getSignId();
    }

    /** 見た目を変えて、保存とクライアントへの同期を予約する（サーバー側で呼ぶ）。 */
    public void setAppearance(SignAppearance appearance) {
        // 同じ値なら保存も同期もしない（連打で通信を増やさない）
        if (this.appearance.equals(appearance)) {
            return;
        }
        this.appearance = appearance;
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
        appearance = SignNbt.read(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        SignNbt.write(compound, appearance);
        return compound;
    }

    // クリエイティブの Ctrl + ピックブロックなどで付く BlockEntityTag を、OP 以外が置いたときは読ませない
    // （図柄 ID の形を確かめずに TileEntity へ入ってしまうため。図柄はアイテムの SignId から形を確かめて引き継ぐ）
    @Override
    public boolean onlyOpsCanSetNbt() {
        return true;
    }

    // 描画の範囲。複数マスの看板は代表のマスから右・上へ広がる（ポール付きは最上段の板だけに描く）ので、向きによらず覆えるよう周り 1 マス・上へ 3 マスまで広げる
    // （既定の 1 マスのままだと、代表のマスが画面の外に出たときに板ごと描かれなくなる）
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(2, 3, 2));
    }

    // 板そのものを TileEntity の描画（SignRenderer）で描くので、既定の 64 ブロックより遠くまで描く（256 ブロック。チャンクの描画距離 16 相当）。
    // これより遠い看板は、ポール付きの支柱と金具だけが見える
    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 256.0 * 256.0;
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
