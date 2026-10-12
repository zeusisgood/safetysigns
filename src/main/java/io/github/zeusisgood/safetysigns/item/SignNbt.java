package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignIds;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

/**
 * 看板の見た目（{@link SignAppearance}）の NBT の読み書き。キーはアイテム（タグの直下）とブロック（TileEntity）で同じ。
 * <ul>
 *   <li>{@value #KEY}: 図柄 ID の文字列。無地ならキーなし</li>
 *   <li>{@value #CUTOUT_KEY}: 切り抜きが OFF のときだけ false を書く。キーなしは ON</li>
 *   <li>{@value #BACK_PATTERN_KEY}: 裏面が図柄のときだけ true を書く。キーなしは銀</li>
 * </ul>
 * 既定の値はキーを書かないので、既定の看板のアイテムは NBT を持たない（無地のアイテムとスタックできる）。
 * キー名は既存のワールドとアイテムが参照するので変えない。
 */
public final class SignNbt {

    public static final String KEY = "SignId";
    public static final String CUTOUT_KEY = "Cutout";
    public static final String BACK_PATTERN_KEY = "BackPattern";

    private SignNbt() {
    }

    /** NBT の見た目。図柄 ID の形は確かめない（読み込んだ ID は消さずに持つ）。 */
    public static SignAppearance read(@Nullable NBTTagCompound compound) {
        if (compound == null) {
            return SignAppearance.DEFAULT;
        }
        String id = compound.hasKey(KEY, Constants.NBT.TAG_STRING) ? compound.getString(KEY) : null;
        boolean cutout = !compound.hasKey(CUTOUT_KEY, Constants.NBT.TAG_BYTE) || compound.getBoolean(CUTOUT_KEY);
        boolean backPattern = compound.hasKey(BACK_PATTERN_KEY, Constants.NBT.TAG_BYTE) && compound.getBoolean(BACK_PATTERN_KEY);
        return new SignAppearance(id, cutout, backPattern);
    }

    /** 見た目を書く。既定の値のキーは消す。 */
    public static void write(NBTTagCompound compound, SignAppearance appearance) {
        if (appearance.getSignId() == null) {
            compound.removeTag(KEY);
        } else {
            compound.setString(KEY, appearance.getSignId());
        }
        if (appearance.isCutout()) {
            compound.removeTag(CUTOUT_KEY);
        } else {
            compound.setBoolean(CUTOUT_KEY, false);
        }
        if (appearance.isBackPattern()) {
            compound.setBoolean(BACK_PATTERN_KEY, true);
        } else {
            compound.removeTag(BACK_PATTERN_KEY);
        }
    }

    /** アイテムの見た目。 */
    public static SignAppearance get(ItemStack stack) {
        return read(stack.getTagCompound());
    }

    /** アイテムの見た目を変える。タグが空になったら外す（無地のアイテムとスタックできるように）。 */
    public static void set(ItemStack stack, SignAppearance appearance) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound == null) {
            if (appearance.equals(SignAppearance.DEFAULT)) {
                return;
            }
            compound = new NBTTagCompound();
            stack.setTagCompound(compound);
        }
        write(compound, appearance);
        if (compound.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    /**
     * 設置するときに引き継ぐ見た目。クリエイティブではクライアントがアイテムの NBT を自由に作れるので、
     * 図柄 ID は形の正しいものだけを引き継ぐ（形が違えば無地にする）。切り抜き・裏面は真偽値なのでそのまま。
     */
    public static SignAppearance getValid(ItemStack stack) {
        SignAppearance appearance = get(stack);
        return SignIds.isValid(appearance.getSignId()) ? appearance : appearance.withSignId(null);
    }
}
