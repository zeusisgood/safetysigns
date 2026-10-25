package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 看板の見た目（{@link SignAppearance}）の NBT の読み書き。キーはアイテム（タグの直下）とブロック（TileEntity）で同じで、1.12.2 版とも同じ。
 * <ul>
 *   <li>{@value #KEY}: 図柄 ID の文字列。無地ならキーなし</li>
 *   <li>{@value #CUTOUT_KEY}: 切り抜きが OFF のときだけ false を書く。キーなしは ON</li>
 *   <li>{@value #BACK_PATTERN_KEY}: 裏面が図柄のときだけ true を書く。キーなしは銀</li>
 *   <li>{@value #GLOW_KEY}: 夜間に光らないときだけ false を書く。キーなしは光る</li>
 * </ul>
 * 既定の値はキーを書かないので、既定の看板のアイテムは NBT を持たない（無地のアイテムとスタックできる）。
 * キー名はワールドとアイテムが参照するので変えない。
 */
public final class SignNbt {

    public static final String KEY = "SignId";
    public static final String CUTOUT_KEY = "Cutout";
    public static final String BACK_PATTERN_KEY = "BackPattern";
    public static final String GLOW_KEY = "Glow";

    private SignNbt() {
    }

    /** NBT の見た目。図柄 ID の形は確かめない（読み込んだ ID は消さずに持つ）。 */
    public static SignAppearance read(@Nullable CompoundTag compound) {
        if (compound == null) {
            return SignAppearance.DEFAULT;
        }
        String id = compound.contains(KEY, Tag.TAG_STRING) ? compound.getString(KEY) : null;
        boolean cutout = !compound.contains(CUTOUT_KEY, Tag.TAG_BYTE) || compound.getBoolean(CUTOUT_KEY);
        boolean backPattern = compound.contains(BACK_PATTERN_KEY, Tag.TAG_BYTE) && compound.getBoolean(BACK_PATTERN_KEY);
        boolean glow = !compound.contains(GLOW_KEY, Tag.TAG_BYTE) || compound.getBoolean(GLOW_KEY);
        return new SignAppearance(id, cutout, backPattern, glow);
    }

    /** 見た目を書く。既定の値のキーは消す。 */
    public static void write(CompoundTag compound, SignAppearance appearance) {
        if (appearance.getSignId() == null) {
            compound.remove(KEY);
        } else {
            compound.putString(KEY, appearance.getSignId());
        }
        if (appearance.isCutout()) {
            compound.remove(CUTOUT_KEY);
        } else {
            compound.putBoolean(CUTOUT_KEY, false);
        }
        if (appearance.isBackPattern()) {
            compound.putBoolean(BACK_PATTERN_KEY, true);
        } else {
            compound.remove(BACK_PATTERN_KEY);
        }
        if (appearance.isGlow()) {
            compound.remove(GLOW_KEY);
        } else {
            compound.putBoolean(GLOW_KEY, false);
        }
    }

    /** アイテムの見た目。 */
    public static SignAppearance get(ItemStack stack) {
        return read(stack.getTag());
    }

    /** アイテムの見た目を変える。タグが空になったら外す（無地のアイテムとスタックできるように）。 */
    public static void set(ItemStack stack, SignAppearance appearance) {
        CompoundTag compound = stack.getTag();
        if (compound == null) {
            if (appearance.equals(SignAppearance.DEFAULT)) {
                return;
            }
            compound = new CompoundTag();
            stack.setTag(compound);
        }
        write(compound, appearance);
        if (compound.isEmpty()) {
            stack.setTag(null);
        }
    }

    /**
     * 設置するときに引き継ぐ見た目。クリエイティブではクライアントがアイテムの NBT を自由に作れるので、
     * 図柄 ID は形の正しいものだけを引き継ぐ（形が違えば無地にする）。切り抜き・裏面・光るかは真偽値なのでそのまま。
     */
    public static SignAppearance getValid(ItemStack stack) {
        SignAppearance appearance = get(stack);
        return SignIds.isValid(appearance.getSignId()) ? appearance : appearance.withSignId(null);
    }
}
