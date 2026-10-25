package io.github.zeusisgood.safetysigns.network;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignIds;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアント → サーバー: 看板の見た目（図柄・切り抜き・裏面・光るか）を変える（GUI で選ぶ・切り替えるたびに送る）。
 * 中身: 看板の位置（long）、図柄があるか（boolean）、図柄 ID（UTF-8、あるときだけ）、切り抜き（boolean）、裏面が図柄か（boolean）、夜間に光るか（boolean）。
 * 1.12.2 版と同じ中身。中身を変えたら互換性を破るのでマイナーを上げる。
 */
public final class MessageSetSign implements CustomPacketPayload {

    public static final Type<MessageSetSign> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SafetySignsMod.MODID, "set_sign"));
    public static final StreamCodec<FriendlyByteBuf, MessageSetSign> STREAM_CODEC =
            StreamCodec.of((buf, message) -> message.toBytes(buf), MessageSetSign::fromBytes);

    /** 看板までの距離の上限（ブロックの中心まで 8 ブロック）。 */
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private final BlockPos pos;
    private final SignAppearance appearance;

    public MessageSetSign(BlockPos pos, SignAppearance appearance) {
        this.pos = pos;
        this.appearance = appearance;
    }

    @Override
    public Type<MessageSetSign> type() {
        return TYPE;
    }

    private static MessageSetSign fromBytes(FriendlyByteBuf buf) {
        BlockPos pos = BlockPos.of(buf.readLong());
        String signId = buf.readBoolean() ? buf.readUtf() : null;
        boolean cutout = buf.readBoolean();
        boolean backPattern = buf.readBoolean();
        boolean glow = buf.readBoolean();
        return new MessageSetSign(pos, new SignAppearance(signId, cutout, backPattern, glow));
    }

    private void toBytes(FriendlyByteBuf buf) {
        String signId = appearance.getSignId();
        buf.writeLong(pos.asLong());
        buf.writeBoolean(signId != null);
        if (signId != null) {
            buf.writeUtf(signId);
        }
        buf.writeBoolean(appearance.isCutout());
        buf.writeBoolean(appearance.isBackPattern());
        buf.writeBoolean(appearance.isGlow());
    }

    public static void handle(MessageSetSign message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, message);
        }
    }

    // クライアントから届いた値を正にしない: ID の形・看板が届く距離にあるか・いじってよい場所かを確かめる（切り抜き・裏面・光るかは真偽値なので形の確認は要らない）
    private static void apply(ServerPlayer player, MessageSetSign message) {
        String signId = message.appearance.getSignId();
        if (signId != null && !SignIds.isValid(signId)) {
            SafetySignsMod.LOGGER.warn("{} sent a malformed sign id; ignored", player.getName().getString());
            return;
        }
        Level world = player.level();
        BlockPos pos = message.pos;
        if (player.isSpectator() || !world.isLoaded(pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQ
                || !world.mayInteract(player, pos)) {
            return;
        }
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEntitySafetySign) {
            ((TileEntitySafetySign) tile).setAppearance(message.appearance);
        }
    }
}
