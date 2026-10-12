package io.github.zeusisgood.safetysigns.block;

import io.github.zeusisgood.safetysigns.SafetySignsMod;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * ブロックと、その設置用アイテムの登録。登録名（wall_sign_1x1 など）は既存ワールドが参照するので変えない。
 */
@Mod.EventBusSubscriber(modid = SafetySignsMod.MODID)
public final class SafetySignsBlocks {

    public static final BlockWallSign WALL_SIGN_1X1 = new BlockWallSign();

    public static final Item WALL_SIGN_1X1_ITEM = new ItemBlock(WALL_SIGN_1X1).setRegistryName(WALL_SIGN_1X1.getRegistryName());

    private SafetySignsBlocks() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(WALL_SIGN_1X1);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(WALL_SIGN_1X1_ITEM);
    }
}
