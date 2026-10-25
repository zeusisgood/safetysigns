package io.github.zeusisgood.safetysigns;

import io.github.zeusisgood.safetysigns.common.SignAppearance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;

import java.util.List;

/** 専用サーバーでの何もしない実装。クライアントでは client/ClientProxy に差し替わる。 */
public class CommonProxy {

    public void init(IEventBus modBus) {
    }

    /** 図柄選択の画面を開く（クライアントだけ）。origin は看板の代表のマス。 */
    public void openSignPicker(BlockPos origin) {
    }

    /** 看板アイテムのツールチップ（図柄の名前はクライアントの一覧にしかない）。 */
    public void appendSignTooltip(SignAppearance appearance, List<Component> tooltip) {
    }
}
