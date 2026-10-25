package io.github.zeusisgood.safetysigns.common;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 看板 1 枚の見た目の設定: 図柄 ID・切り抜き・裏面・夜間に光るか（変えられない値）。看板（TileEntity）・アイテム・通信で同じものを持ち運ぶ。
 * <ul>
 *   <li>図柄 ID: null は無地</li>
 *   <li>切り抜き: true は図柄の形に切り抜く（既定）、false は四角い板の下地に図柄を重ねる。無地は見た目が変わらないので、常に true にそろえる
 *   （切り抜き OFF の無地が NBT を持ち、ふつうの無地とスタックしなくなるのを防ぐ）</li>
 *   <li>裏面: false は銀の無地（既定）、true は裏にも表と同じ図柄（裏から見て読める向き）</li>
 *   <li>夜間に光る: true は表の図柄を明るさ最大で描く（既定）、false は周りの明るさのまま。周りは照らさない</li>
 * </ul>
 */
public final class SignAppearance {

    /** 既定（無地・切り抜き ON・裏面 銀・光る）。NBT にキーが 1 つもない看板・アイテム。 */
    public static final SignAppearance DEFAULT = new SignAppearance(null, true, false, true);

    @Nullable
    private final String signId;
    private final boolean cutout;
    private final boolean backPattern;
    private final boolean glow;

    public SignAppearance(@Nullable String signId, boolean cutout, boolean backPattern, boolean glow) {
        this.signId = signId == null || signId.isEmpty() ? null : signId;
        this.cutout = cutout || this.signId == null;
        this.backPattern = backPattern;
        this.glow = glow;
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

    public boolean isGlow() {
        return glow;
    }

    public SignAppearance withSignId(@Nullable String id) {
        return new SignAppearance(id, cutout, backPattern, glow);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SignAppearance)) {
            return false;
        }
        SignAppearance appearance = (SignAppearance) other;
        return Objects.equals(signId, appearance.signId) && cutout == appearance.cutout
                && backPattern == appearance.backPattern && glow == appearance.glow;
    }

    @Override
    public int hashCode() {
        return Objects.hash(signId, cutout, backPattern, glow);
    }

    @Override
    public String toString() {
        return "SignAppearance{" + signId + ", cutout=" + cutout + ", backPattern=" + backPattern + ", glow=" + glow + "}";
    }
}
