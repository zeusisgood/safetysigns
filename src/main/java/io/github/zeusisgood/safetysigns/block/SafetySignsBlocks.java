package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import io.github.zeusisgood.safetysigns.item.ItemSafetySign;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ブロック・設置用アイテム・TileEntity の登録。登録名（wall_sign_1x1・wall_sign_2x2・sign など）は既存ワールドが参照するので変えない。
 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID)
public final class SafetySignsBlocks {

    public static final BlockWallSign WALL_SIGN_1X1 = new BlockWallSign();

    public static final BlockWallSign2x2 WALL_SIGN_2X2 = new BlockWallSign2x2();

    public static final Item WALL_SIGN_1X1_ITEM = new ItemSafetySign(WALL_SIGN_1X1);
    public static final Item WALL_SIGN_2X2_ITEM = new ItemSafetySign(WALL_SIGN_2X2);

    /** 看板アイテムの全部（モデルの登録などで使う）。 */
    public static final List<Item> SIGN_ITEMS = Collections.unmodifiableList(Arrays.asList(WALL_SIGN_1X1_ITEM, WALL_SIGN_2X2_ITEM));

    private SafetySignsBlocks() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(WALL_SIGN_1X1, WALL_SIGN_2X2);
        // 看板の種類（1×1・2×2・ポール付き）で共通の TileEntity
        GameRegistry.registerTileEntity(TileEntitySafetySign.class, new ResourceLocation(SafetySignsMod.MODID, "sign"));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        SIGN_ITEMS.forEach(event.getRegistry()::register);
    }
}
