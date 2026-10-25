package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ブロック・設置用アイテム・TileEntity の登録。登録名（wall_sign_1x1・wall_sign_2x2・pole_sign・sign）は 1.12.2 版と同じ。
 */
public final class SafetySignsBlocks {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SafetySignsMod.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SafetySignsMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> TILES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SafetySignsMod.MODID);

    public static final RegistryObject<BlockWallSign> WALL_SIGN_1X1 = BLOCKS.register("wall_sign_1x1", BlockWallSign::new);
    public static final RegistryObject<BlockWallSign2x2> WALL_SIGN_2X2 = BLOCKS.register("wall_sign_2x2", BlockWallSign2x2::new);
    public static final RegistryObject<BlockPoleSign> POLE_SIGN = BLOCKS.register("pole_sign", BlockPoleSign::new);

    public static final RegistryObject<Item> WALL_SIGN_1X1_ITEM = ITEMS.register("wall_sign_1x1", () -> new ItemSafetySign(WALL_SIGN_1X1.get()));
    public static final RegistryObject<Item> WALL_SIGN_2X2_ITEM = ITEMS.register("wall_sign_2x2", () -> new ItemSafetySign(WALL_SIGN_2X2.get()));
    public static final RegistryObject<Item> POLE_SIGN_ITEM = ITEMS.register("pole_sign", () -> new ItemSafetySign(POLE_SIGN.get()));

    /** 看板アイテムの全部（モデルの登録・クリエイティブタブで使う）。 */
    public static final List<RegistryObject<Item>> SIGN_ITEMS =
            Collections.unmodifiableList(Arrays.asList(WALL_SIGN_1X1_ITEM, WALL_SIGN_2X2_ITEM, POLE_SIGN_ITEM));

    // 看板の種類（1×1・2×2・ポール付き）で共通の TileEntity
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TileEntitySafetySign>> SIGN_TILE = TILES.register("sign",
            () -> BlockEntityType.Builder.of(TileEntitySafetySign::new, WALL_SIGN_1X1.get(), WALL_SIGN_2X2.get(), POLE_SIGN.get())
                    .build(null));

    private SafetySignsBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TILES.register(modBus);
        modBus.addListener(SafetySignsBlocks::onBuildCreativeTab);
    }

    // 自分のタブは作らず、バニラの看板と同じ「機能的ブロック」に並べる
    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            SIGN_ITEMS.forEach(item -> event.accept(item.get()));
        }
    }
}
