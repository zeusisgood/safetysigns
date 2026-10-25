package io.github.zeusisgood.safetysigns.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zeusisgood.safetysigns.block.SafetySignsBlocks;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 看板の見た目（{@link SignAppearance}）の読み書き。
 * ブロック（TileEntity）は NBT で、キーは 1.12.2・1.20.1 版と同じ:
 * <ul>
 *   <li>{@value #KEY}: 図柄 ID の文字列。無地ならキーなし</li>
 *   <li>{@value #CUTOUT_KEY}: 切り抜きが OFF のときだけ false を書く。キーなしは ON</li>
 *   <li>{@value #BACK_PATTERN_KEY}: 裏面が図柄のときだけ true を書く。キーなしは銀</li>
 *   <li>{@value #GLOW_KEY}: 夜間に光らないときだけ false を書く。キーなしは光る</li>
 * </ul>
 * アイテムはデータコンポーネント {@code safetysigns:sign_appearance}（{@link SafetySignsBlocks#SIGN_APPEARANCE}）。
 * 中の値の名前は sign_id・cutout・back_pattern・glow で、既定の値は書かない。既定の見た目ならコンポーネントを付けない（無地のアイテムとスタックできる）。
 * キー名はワールドとアイテムが参照するので変えない。
 */
public final class SignNbt {

    public static final String KEY = "SignId";
    public static final String CUTOUT_KEY = "Cutout";
    public static final String BACK_PATTERN_KEY = "BackPattern";
    public static final String GLOW_KEY = "Glow";

    /** アイテムのコンポーネントの保存の形。 */
    public static final Codec<SignAppearance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("sign_id").forGetter(a -> Optional.ofNullable(a.getSignId())),
            Codec.BOOL.optionalFieldOf("cutout", true).forGetter(SignAppearance::isCutout),
            Codec.BOOL.optionalFieldOf("back_pattern", false).forGetter(SignAppearance::isBackPattern),
            Codec.BOOL.optionalFieldOf("glow", true).forGetter(SignAppearance::isGlow)
    ).apply(instance, (id, cutout, backPattern, glow) -> new SignAppearance(id.orElse(null), cutout, backPattern, glow)));

    /** アイテムのコンポーネントの通信の形。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, SignAppearance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), a -> Optional.ofNullable(a.getSignId()),
            ByteBufCodecs.BOOL, SignAppearance::isCutout,
            ByteBufCodecs.BOOL, SignAppearance::isBackPattern,
            ByteBufCodecs.BOOL, SignAppearance::isGlow,
            (id, cutout, backPattern, glow) -> new SignAppearance(id.orElse(null), cutout, backPattern, glow));

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
        return stack.getOrDefault(SafetySignsBlocks.SIGN_APPEARANCE.get(), SignAppearance.DEFAULT);
    }

    /** アイテムの見た目を変える。既定の見た目ならコンポーネントを外す（無地のアイテムとスタックできるように）。 */
    public static void set(ItemStack stack, SignAppearance appearance) {
        if (appearance.equals(SignAppearance.DEFAULT)) {
            stack.remove(SafetySignsBlocks.SIGN_APPEARANCE.get());
        } else {
            stack.set(SafetySignsBlocks.SIGN_APPEARANCE.get(), appearance);
        }
    }

    /**
     * 設置するときに引き継ぐ見た目。クリエイティブではクライアントがアイテムのコンポーネントを自由に作れるので、
     * 図柄 ID は形の正しいものだけを引き継ぐ（形が違えば無地にする）。切り抜き・裏面・光るかは真偽値なのでそのまま。
     */
    public static SignAppearance getValid(ItemStack stack) {
        SignAppearance appearance = get(stack);
        return SignIds.isValid(appearance.getSignId()) ? appearance : appearance.withSignId(null);
    }
}
