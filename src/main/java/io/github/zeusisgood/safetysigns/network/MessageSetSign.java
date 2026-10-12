package io.github.zeusisgood.safetysigns.network;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
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

/**
 * クライアント → サーバー: 看板の見た目（図柄・切り抜き・裏面）を変える（GUI で選ぶ・切り替えるたびに送る）。
 * 中身: 看板の位置（long）、図柄があるか（boolean）、図柄 ID（UTF-8、あるときだけ）、切り抜き（boolean）、裏面が図柄か（boolean）。
 * 中身を変えたら互換性を破るのでマイナーを上げる。
 */
public class MessageSetSign implements IMessage {

    /** 看板までの距離の上限（ブロックの中心まで 8 ブロック。バニラがブロックの右クリックを受け付ける距離と同じ）。 */
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private BlockPos pos;
    private SignAppearance appearance;

    public MessageSetSign() {
    }

    public MessageSetSign(BlockPos pos, SignAppearance appearance) {
        this.pos = pos;
        this.appearance = appearance;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        String signId = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
        boolean cutout = buf.readBoolean();
        boolean backPattern = buf.readBoolean();
        appearance = new SignAppearance(signId, cutout, backPattern);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        String signId = appearance.getSignId();
        buf.writeLong(pos.toLong());
        buf.writeBoolean(signId != null);
        if (signId != null) {
            ByteBufUtils.writeUTF8String(buf, signId);
        }
        buf.writeBoolean(appearance.isCutout());
        buf.writeBoolean(appearance.isBackPattern());
    }

    public static class Handler implements IMessageHandler<MessageSetSign, IMessage> {
        @Override
        public IMessage onMessage(MessageSetSign message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        // クライアントから届いた値を正にしない: ID の形・看板が届く距離にあるか・いじってよい場所かを確かめる（切り抜き・裏面は真偽値なので形の確認は要らない）
        private static void apply(EntityPlayerMP player, MessageSetSign message) {
            String signId = message.appearance.getSignId();
            if (signId != null && !SignIds.isValid(signId)) {
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
                ((TileEntitySafetySign) tile).setAppearance(message.appearance);
            }
        }
    }
}
