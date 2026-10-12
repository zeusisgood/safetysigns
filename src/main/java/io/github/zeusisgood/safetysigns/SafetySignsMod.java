package io.github.zeusisgood.safetysigns;

import net.minecraftforge.fml.common.Mod;

/**
 * サーバーとクライアントの両方に必要。同じマイナー同士（0.1.x）なら、パッチが違っても接続できる。
 * 互換性を破ったらマイナーを上げ、ACCEPTED_VERSIONS も新しいマイナーに書き換える（CLAUDE.md のバージョンの方針）。
 */
@Mod(modid = SafetySignsMod.MODID, name = SafetySignsMod.NAME, version = SafetySignsMod.VERSION,
        acceptableRemoteVersions = SafetySignsMod.ACCEPTED_VERSIONS)
public class SafetySignsMod {

    public static final String MODID = "safetysigns";
    public static final String NAME = "Safety Signs";
    /** build.gradle の version と必ず揃える。 */
    public static final String VERSION = "0.1.0";
    /** 接続できる相手のバージョンの範囲（Maven の書式）。 */
    public static final String ACCEPTED_VERSIONS = "[0.1,0.2)";
}
