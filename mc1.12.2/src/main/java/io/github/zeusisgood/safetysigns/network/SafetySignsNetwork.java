package io.github.zeusisgood.safetysigns.network;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** 独自の通信路。メッセージの番号は通信内容の一部なので、並びを変えない（足すときは末尾に）。 */
public final class SafetySignsNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(SafetySignsMod.MODID);

    private SafetySignsNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(MessageSetSign.Handler.class, MessageSetSign.class, 0, Side.SERVER);
    }
}
