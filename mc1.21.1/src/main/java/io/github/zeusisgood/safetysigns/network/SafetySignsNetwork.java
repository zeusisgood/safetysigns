package io.github.zeusisgood.safetysigns.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 独自の通信路。両方に Mod が要るので、optional にしない（入っていない相手とは接続できない）。
 */
public final class SafetySignsNetwork {

    /** 接続できる相手の版の印（マイナーの番号）。マイナーを上げたら変える（1.12.2 の ACCEPTED_VERSIONS と同じ考え）。 */
    public static final String PROTOCOL = "1.1";

    private SafetySignsNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        // ハンドラーはメインのスレッドで呼ばれる（NeoForge の既定）
        registrar.playToServer(MessageSetSign.TYPE, MessageSetSign.STREAM_CODEC, MessageSetSign::handle);
    }
}
