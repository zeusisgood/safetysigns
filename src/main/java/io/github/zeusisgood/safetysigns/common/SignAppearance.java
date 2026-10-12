package io.github.zeusisgood.safetysigns.common;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 看板 1 枚の見た目の設定: 図柄 ID・切り抜き・裏面（変えられない値）。看板（TileEntity）・アイテム・通信で同じものを持ち運ぶ。
 * <ul>
 *   <li>図柄 ID: null は無地</li>
 *   <li>切り抜き: true は図柄の形に切り抜く（既定）、false は四角い板の下地に図柄を重ねる</li>
 *   <li>裏面: false は銀の無地（既定）、true は裏にも表と同じ図柄（裏から見て読める向き）</li>
 * </ul>
 */
public final class SignAppearance {

    /** 既定（無地・切り抜き ON・裏面 銀）。NBT にキーが 1 つもない看板・アイテム。 */
    public static final SignAppearance DEFAULT = new SignAppearance(null, true, false);

    @Nullable
    private final String signId;
    private final boolean cutout;
    private final boolean backPattern;

    public SignAppearance(@Nullable String signId, boolean cutout, boolean backPattern) {
        this.signId = signId == null || signId.isEmpty() ? null : signId;
        this.cutout = cutout;
        this.backPattern = backPattern;
    }

    @Nullable
    public String getSignId() {
        return signId;
    }

    public boolean isCutout() {
        return cutout;
    }

    public boolean isBackPattern() {
        return backPattern;
    }

    public SignAppearance withSignId(@Nullable String id) {
        return new SignAppearance(id, cutout, backPattern);
    }

    public SignAppearance withCutout(boolean value) {
        return new SignAppearance(signId, value, backPattern);
    }

    public SignAppearance withBackPattern(boolean value) {
        return new SignAppearance(signId, cutout, value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SignAppearance)) {
            return false;
        }
        SignAppearance appearance = (SignAppearance) other;
        return Objects.equals(signId, appearance.signId) && cutout == appearance.cutout
                && backPattern == appearance.backPattern;
    }

    @Override
    public int hashCode() {
        return Objects.hash(signId, cutout, backPattern);
    }

    @Override
    public String toString() {
        return "SignAppearance{" + signId + ", cutout=" + cutout + ", backPattern=" + backPattern + "}";
    }
}
