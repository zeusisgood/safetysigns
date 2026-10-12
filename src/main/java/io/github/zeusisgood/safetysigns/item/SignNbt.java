package io.github.zeusisgood.safetysigns.item;

import io.github.zeusisgood.safetysigns.common.SignIds;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

/**
 * 図柄 ID の NBT の読み書き。キーはアイテム（タグの直下）とブロック（TileEntity）で同じ {@value #KEY}。
 * キー名は既存のワールドとアイテムが参照するので変えない。
 */
public final class SignNbt {

    public static final String KEY = "SignId";

    private SignNbt() {
    }

    /** NBT の図柄 ID。キーがない・空なら null（無地）。形は確かめない（読み込んだ ID は消さずに持つ）。 */
    @Nullable
    public static String readSignId(@Nullable NBTTagCompound compound) {
        if (compound == null || !compound.hasKey(KEY, Constants.NBT.TAG_STRING)) {
            return null;
        }
        String id = compound.getString(KEY);
        return id.isEmpty() ? null : id;
    }

    public static void writeSignId(NBTTagCompound compound, @Nullable String signId) {
        if (signId == null || signId.isEmpty()) {
            compound.removeTag(KEY);
        } else {
            compound.setString(KEY, signId);
        }
    }

    /** アイテムの図柄 ID。null は無地。 */
    @Nullable
    public static String getSignId(ItemStack stack) {
        return readSignId(stack.getTagCompound());
    }

    /**
     * 設置するときに引き継ぐ図柄 ID。クリエイティブではクライアントがアイテムの NBT を自由に作れるので、
     * 形の正しい ID だけを引き継ぐ（形が違えば無地にする）。
     */
    @Nullable
    public static String getValidSignId(ItemStack stack) {
        String id = getSignId(stack);
        return SignIds.isValid(id) ? id : null;
    }
}
