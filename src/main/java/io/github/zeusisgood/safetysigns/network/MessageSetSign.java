package io.github.zeusisgood.safetysigns.network;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.SignIds;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

/**
 * クライアント → サーバー: 看板の図柄を変える（GUI で選ぶたびに送る）。
 * 中身: 看板の位置（long）、図柄があるか（boolean）、図柄 ID（UTF-8、あるときだけ）。
 * 中身を変えたら互換性を破るのでマイナーを上げる。
 */
public class MessageSetSign implements IMessage {

    /** 看板までの距離の上限（ブロックの中心まで 8 ブロック。バニラがブロックの右クリックを受け付ける距離と同じ）。 */
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private BlockPos pos;
    @Nullable
    private String signId;

    public MessageSetSign() {
    }

    /** @param signId null は無地に戻す */
    public MessageSetSign(BlockPos pos, @Nullable String signId) {
        this.pos = pos;
        this.signId = signId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        signId = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeBoolean(signId != null);
        if (signId != null) {
            ByteBufUtils.writeUTF8String(buf, signId);
        }
    }

    public static class Handler implements IMessageHandler<MessageSetSign, IMessage> {
        @Override
        public IMessage onMessage(MessageSetSign message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        // クライアントから届いた値を正にしない: ID の形・看板が届く距離にあるか・いじってよい場所かを確かめる
        private static void apply(EntityPlayerMP player, MessageSetSign message) {
            if (message.signId != null && !SignIds.isValid(message.signId)) {
                SafetySignsMod.LOGGER.warn("{} sent a malformed sign id; ignored", player.getName());
                return;
            }
            World world = player.world;
            BlockPos pos = message.pos;
            if (player.isSpectator() || !world.isBlockLoaded(pos)
                    || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQ
                    || !world.isBlockModifiable(player, pos)) {
                return;
            }
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntitySafetySign) {
                ((TileEntitySafetySign) tile).setSignId(message.signId);
            }
        }
    }
}
