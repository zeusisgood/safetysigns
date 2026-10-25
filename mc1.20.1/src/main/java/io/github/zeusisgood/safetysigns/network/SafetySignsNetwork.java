package io.github.zeusisgood.safetysigns.network;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 独自の通信路。メッセージの番号は通信内容の一部なので、並びを変えない（足すときは末尾に）。
 * 両方に Mod が要るので、相手に同じ印のチャンネルがなければ接続できない。
 */
public final class SafetySignsNetwork {

    /** 接続できる相手の版の印（マイナーの番号）。マイナーを上げたら変える（1.12.2 の ACCEPTED_VERSIONS と同じ考え）。 */
    public static final String PROTOCOL = "1.1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SafetySignsMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SafetySignsNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(MessageSetSign.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MessageSetSign::toBytes)
                .decoder(MessageSetSign::fromBytes)
                .consumerMainThread(MessageSetSign::handle)
                .add();
    }
}
