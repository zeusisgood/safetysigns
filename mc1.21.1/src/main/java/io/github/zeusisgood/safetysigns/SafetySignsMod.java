package io.github.zeusisgood.safetysigns;

import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import io.github.zeusisgood.safetysigns.network.SafetySignsNetwork;
import io.github.zeusisgood.safetysigns.recipe.SafetySignsRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * サーバーとクライアントの両方に必要。同じマイナー同士なら、パッチが違っても接続できる（{@link SafetySignsNetwork#PROTOCOL}）。
 * 互換性を破ったらマイナーを上げ、PROTOCOL も新しいマイナーに書き換える（CLAUDE.md のバージョンの方針）。
 */
@Mod(SafetySignsMod.MODID)
public final class SafetySignsMod {

    public static final String MODID = "safetysigns";
    public static final String NAME = "Safety Signs";
    /** gradle.properties の mod_version と必ず揃える。 */
    public static final String VERSION = "1.1.1";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** クライアントだけの処理（画面を開く・ツールチップ）の入口。専用サーバーではクライアントのクラスを読まない。 */
    public static CommonProxy proxy = new CommonProxy();

    public SafetySignsMod(IEventBus modBus) {
        SafetySignsBlocks.register(modBus);
        SafetySignsRecipes.register(modBus);
        modBus.addListener(SafetySignsNetwork::register);
        if (FMLEnvironment.dist.isClient()) {
            proxy = newClientProxy();
        }
        proxy.init(modBus);
    }

    /** 物理サーバーではクライアントのクラスを読まない（名前を文字列で持つ）。 */
    private static CommonProxy newClientProxy() {
        try {
            return (CommonProxy) Class.forName("io.github.zeusisgood.safetysigns.client.ClientProxy")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
