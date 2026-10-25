package io.github.zeusisgood.safetysigns.common;

import java.util.regex.Pattern;

/**
 * 図柄 ID（{@code <namespace>:<名前>}）の形の確認。
 * サーバーはクライアントから届いた ID をここで確かめる（図柄の一覧は知らないので、形だけを見る）。
 */
public final class SignIds {

    /** 図柄 ID の最大の長さ（文字数）。 */
    public static final int MAX_LENGTH = 64;

    // 名前空間・名前とも、Minecraft のリソースの名前と同じ文字（小文字・数字・_ . -、名前は / も）だけ
    private static final Pattern FORM = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private SignIds() {
    }

    /** 形が正しい図柄 ID なら true。null・空・64 文字を超えるものは false。 */
    public static boolean isValid(String id) {
        return id != null && id.length() <= MAX_LENGTH && FORM.matcher(id).matches();
    }

    /** 図柄 ID の名前空間（: より前）。形が正しい ID だけに使う。 */
    public static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }
}
