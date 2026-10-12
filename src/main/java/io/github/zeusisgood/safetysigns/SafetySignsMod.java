package io.github.zeusisgood.safetysigns;

import io.github.zeusisgood.safetysigns.network.SafetySignsNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * サーバーとクライアントの両方に必要。同じマイナー同士（0.6.x）なら、パッチが違っても接続できる。
 * 互換性を破ったらマイナーを上げ、ACCEPTED_VERSIONS も新しいマイナーに書き換える（CLAUDE.md のバージョンの方針）。
 */
@Mod(modid = SafetySignsMod.MODID, name = SafetySignsMod.NAME, version = SafetySignsMod.VERSION,
        acceptableRemoteVersions = SafetySignsMod.ACCEPTED_VERSIONS)
public class SafetySignsMod {

    public static final String MODID = "safetysigns";
    public static final String NAME = "Safety Signs";
    /** build.gradle の version と必ず揃える。 */
    public static final String VERSION = "0.6.0";
    /** 接続できる相手のバージョンの範囲（Maven の書式）。 */
    public static final String ACCEPTED_VERSIONS = "[0.6,0.7)";

    /** GUI の番号: 図柄選択。 */
    public static final int GUI_SIGN_PICKER = 0;

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static SafetySignsMod instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SafetySignsNetwork.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new SafetySignsGuiHandler());
    }
}
