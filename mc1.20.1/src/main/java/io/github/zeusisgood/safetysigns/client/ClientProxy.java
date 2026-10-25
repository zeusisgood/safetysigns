package io.github.zeusisgood.safetysigns.client;

import io.github.zeusisgood.safetysigns.CommonProxy;
import io.github.zeusisgood.safetysigns.block.BlockSafetySign;
import io.github.zeusisgood.safetysigns.client.gui.GuiSignPicker;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;

/** クライアントだけの処理。SafetySignsMod がクライアントでだけ名前から作る。 */
public final class ClientProxy extends CommonProxy {

    @Override
    public void init(IEventBus modBus) {
        ClientModels.register(modBus);
    }

    @Override
    public void openSignPicker(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        Level world = minecraft.level;
        if (world == null) {
            return;
        }
        BlockState state = world.getBlockState(origin);
        if (state.getBlock() instanceof BlockSafetySign && world.getBlockEntity(origin) instanceof TileEntitySafetySign tile) {
            // プレビューは図柄を描く面の縦横比（ポール付きは支柱を除いた最上段の 1×1）
            BlockSafetySign block = (BlockSafetySign) state.getBlock();
            minecraft.setScreen(new GuiSignPicker(origin, tile.getAppearance(), block.getLayout().getWidth(), block.getFaceHeight()));
        }
    }

    @Override
    public void appendSignTooltip(SignAppearance appearance, List<Component> tooltip) {
        tooltip.add(SignTextures.describe(appearance.getSignId()));
        // 設定の行はバニラの補足と同じ灰色。無地は常に切り抜き ON なので、形の行が出るのは図柄のある看板だけ
        if (!appearance.isCutout()) {
            tooltip.add(Component.translatable("tooltip.safetysigns.no_cutout").withStyle(ChatFormatting.GRAY));
        }
        if (appearance.isBackPattern()) {
            tooltip.add(Component.translatable("tooltip.safetysigns.double_sided").withStyle(ChatFormatting.GRAY));
        }
        if (!appearance.isGlow()) {
            tooltip.add(Component.translatable("tooltip.safetysigns.no_glow").withStyle(ChatFormatting.GRAY));
        }
    }
}
