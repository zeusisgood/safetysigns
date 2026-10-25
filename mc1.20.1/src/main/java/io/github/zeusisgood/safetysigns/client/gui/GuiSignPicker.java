package io.github.zeusisgood.safetysigns.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.zeusisgood.safetysigns.client.SignTextures;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignCatalog;
import io.github.zeusisgood.safetysigns.common.SignCategory;
import io.github.zeusisgood.safetysigns.common.SignDefinition;
import io.github.zeusisgood.safetysigns.network.MessageSetSign;
import io.github.zeusisgood.safetysigns.network.SafetySignsNetwork;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 図柄選択の GUI。上にカテゴリのタブ（W P M E F GHS その他）、左に図柄のグリッド、右にプレビューとその下に夜間に光るかの切り替え、下に検索欄と「無地に戻す」、その下に切り抜き・裏面の切り替え。
 * 図柄をクリック・設定を切り替えるたびにサーバーへ送る（決定ボタンなし）。コンテナを持たないので、クライアントだけで開く。
 * 配置と色は 1.12.2 版と同じ。
 */
public class GuiSignPicker extends Screen {

    // 色（バニラ風の灰色パネル）
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int TEXT = 0x404040;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TAB_IDLE = 0xFF8B8B8B;
    private static final int TAB_DISABLED = 0xFFB5B5B5;

    // 大きさと配置（パネルの左上から）
    private static final int WIDTH = 256;
    private static final int HEIGHT = 224;
    private static final int TAB_Y = 18;
    /** タブの幅の最小（1 文字の W P M E F の幅）。文字の長いタブ（GHS・その他）は文字に合わせて広げる。 */
    private static final int TAB_W = 24;
    private static final int TAB_GAP = 2;
    private static final int TAB_H = 16;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 40;
    private static final int CELL = 20;
    private static final int COLUMNS = 6;
    private static final int ROWS = 6;
    private static final int SCROLL_X = GRID_X + COLUMNS * CELL + 2;
    private static final int SCROLL_W = 6;
    private static final int PREVIEW_X = 146;
    private static final int PREVIEW_W = WIDTH - PREVIEW_X - 8;
    private static final int PREVIEW_SIZE = 64;
    /** プレビューの下の、夜間に光るかの切り替え。 */
    private static final int GLOW_Y = GRID_Y + PREVIEW_SIZE + 4;
    private static final int BOTTOM_Y = 172;
    /** 2 段目（切り抜き・裏面の切り替え）。 */
    private static final int OPTIONS_Y = BOTTOM_Y + 24;

    /** 看板の代表のマス。 */
    private final BlockPos pos;
    /** 看板の幅と高さ（ブロック）。プレビューをこの縦横比で描く。 */
    private final int signWidth;
    private final int signHeight;
    /** GUI で選んでいる図柄 ID（null は無地）。サーバーへ送った値。 */
    @Nullable
    private String selected;
    /** 切り抜き（true が既定）・裏面（true が図柄）・夜間に光る（true が既定）。サーバーへ送った値。 */
    private boolean cutout;
    private boolean backPattern;
    private boolean glow;

    /** 選んでいるタブ。検索中は null（全カテゴリ横断）。 */
    @Nullable
    private SignCategory tab;
    private List<SignDefinition> visible = Collections.emptyList();
    private int scrollRow;
    private boolean draggingScrollbar;

    private EditBox search;
    private Button blankButton;
    private Button cutoutButton;
    private Button backButton;
    private Button glowButton;
    private int left;
    private int top;

    public GuiSignPicker(BlockPos pos, SignAppearance current, int signWidth, int signHeight) {
        super(Component.translatable("gui.safetysigns.picker.title"));
        this.pos = pos;
        this.selected = current.getSignId();
        this.cutout = current.isCutout();
        this.backPattern = current.isBackPattern();
        this.glow = current.isGlow();
        this.signWidth = signWidth;
        this.signHeight = signHeight;
    }

    private SignCatalog catalog() {
        return SignTextures.catalog();
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        String previousText = search != null ? search.getValue() : "";
        search = new EditBox(font, left + GRID_X + 1, top + BOTTOM_Y + 1, COLUMNS * CELL + SCROLL_W, 18,
                Component.translatable("gui.safetysigns.search"));
        search.setMaxLength(64);
        search.setValue(previousText);
        search.setHint(Component.translatable("gui.safetysigns.search"));
        search.setResponder(text -> onSearchChanged());
        addRenderableWidget(search);
        setInitialFocus(search);

        blankButton = addRenderableWidget(Button.builder(Component.translatable("gui.safetysigns.reset_blank"), b -> select(null))
                .bounds(left + PREVIEW_X, top + BOTTOM_Y, PREVIEW_W, 20).build());
        cutoutButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            cutout = !cutout;
            refreshOptionButtons();
            send();
        }).bounds(left + GRID_X, top + OPTIONS_Y, COLUMNS * CELL + SCROLL_W + 2, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            backPattern = !backPattern;
            refreshOptionButtons();
            send();
        }).bounds(left + PREVIEW_X, top + OPTIONS_Y, PREVIEW_W, 20).build());
        glowButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            glow = !glow;
            refreshOptionButtons();
            send();
        }).bounds(left + PREVIEW_X, top + GLOW_Y, PREVIEW_W, 20).build());
        refreshOptionButtons();

        if (tab == null && previousText.isEmpty()) {
            // 最初は今の図柄のカテゴリ。無地・一覧にない図柄なら、図柄のある最初のカテゴリ
            SignDefinition current = catalog().get(selected);
            tab = current != null ? current.getCategory() : firstNonEmptyTab();
        }
        refreshVisible();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        search.tick();
        // 看板が壊れた・離れたら閉じる（pos は代表のマス。どのマスが壊れても全体が消えるので、ここだけ見ればよい）
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !(minecraft.level.getBlockEntity(pos) instanceof TileEntitySafetySign)
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) > MessageSetSign.MAX_DISTANCE_SQ) {
            onClose();
        }
    }

    @Nullable
    private SignCategory firstNonEmptyTab() {
        for (SignCategory category : SignCategory.values()) {
            if (!catalog().byCategory(category).isEmpty()) {
                return category;
            }
        }
        return null;
    }

    private void refreshVisible() {
        String text = search.getValue();
        if (!text.trim().isEmpty()) {
            visible = catalog().search(text);
        } else if (tab != null) {
            visible = catalog().byCategory(tab);
        } else {
            visible = Collections.emptyList();
        }
        scrollRow = Math.min(scrollRow, maxScroll());
        blankButton.active = selected != null;
    }

    /** 切り抜き・裏面・夜間に光るかのボタンの文字と押せるか。無地は形が四角しかないので、切り抜きは ON のまま押せない表示にする（光るかは無地でも押せる）。 */
    private void refreshOptionButtons() {
        cutoutButton.setMessage(Component.translatable("gui.safetysigns.cutout",
                Component.translatable(cutout ? "gui.safetysigns.cutout.on" : "gui.safetysigns.cutout.off")));
        cutoutButton.active = selected != null;
        backButton.setMessage(Component.translatable("gui.safetysigns.back",
                Component.translatable(backPattern ? "gui.safetysigns.back.pattern" : "gui.safetysigns.back.silver")));
        glowButton.setMessage(Component.translatable("gui.safetysigns.glow",
                Component.translatable(glow ? "gui.safetysigns.glow.on" : "gui.safetysigns.glow.off")));
    }

    private int maxScroll() {
        int rows = (visible.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - ROWS);
    }

    private void select(@Nullable String signId) {
        if (signId == null ? selected == null : signId.equals(selected)) {
            return;
        }
        selected = signId;
        if (signId == null) {
            // 無地は常に切り抜き ON（SignAppearance と同じ）。次に図柄を選んだときも ON から始まる
            cutout = true;
        }
        blankButton.active = selected != null;
        refreshOptionButtons();
        send();
    }

    /** 今の選択（図柄・切り抜き・裏面・光るか）をサーバーへ送る。サーバーが距離などを確かめてから書き込む。 */
    private void send() {
        SafetySignsNetwork.CHANNEL.sendToServer(new MessageSetSign(pos, new SignAppearance(selected, cutout, backPattern, glow)));
    }

    // ---- 入力 ----

    private void onSearchChanged() {
        if (!search.getValue().trim().isEmpty()) {
            // 検索中は全カテゴリ横断。タブの選択を外す
            tab = null;
        } else if (tab == null) {
            SignDefinition current = catalog().get(selected);
            tab = current != null ? current.getCategory() : firstNonEmptyTab();
        }
        scrollRow = 0;
        refreshVisible();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) {
            return false;
        }
        SignCategory clickedTab = tabAt(mouseX, mouseY);
        if (clickedTab != null && !catalog().byCategory(clickedTab).isEmpty()) {
            // タブを押したら検索をやめる（検索欄の変化で onSearchChanged が呼ばれるので、タブはそのあとで決める）
            search.setValue("");
            tab = clickedTab;
            scrollRow = 0;
            refreshVisible();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        SignDefinition clicked = cellAt(mouseX, mouseY);
        if (clicked != null) {
            select(clicked.getId());
            return true;
        }
        if (inScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void scrollTo(double mouseY) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        float ratio = (float) (mouseY - (top + GRID_Y)) / (ROWS * CELL);
        scrollRow = Math.max(0, Math.min(max, Math.round(ratio * max)));
    }

    @Nullable
    private SignCategory tabAt(double mouseX, double mouseY) {
        for (SignCategory category : SignCategory.values()) {
            int x = left + tabX(category);
            if (mouseX >= x && mouseX < x + tabWidth(category) && mouseY >= top + TAB_Y && mouseY < top + TAB_Y + TAB_H) {
                return category;
            }
        }
        return null;
    }

    /** タブの文字。ISO 7010 の分類と GHS は分類の名前そのまま、その他は言語ファイルの文字。 */
    private static Component tabLabel(SignCategory category) {
        return category == SignCategory.OTHER ? Component.translatable("gui.safetysigns.tab.other") : Component.literal(category.name());
    }

    private int tabWidth(SignCategory category) {
        return Math.max(TAB_W, font.width(tabLabel(category)) + 10);
    }

    /** タブの左端（パネルの左から）。前のタブの幅を足していく。 */
    private int tabX(SignCategory category) {
        int x = GRID_X;
        for (SignCategory before : SignCategory.values()) {
            if (before == category) {
                break;
            }
            x += tabWidth(before) + TAB_GAP;
        }
        return x;
    }

    @Nullable
    private SignDefinition cellAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - (left + GRID_X)) / CELL);
        int row = (int) Math.floor((mouseY - (top + GRID_Y)) / CELL);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return null;
        }
        int index = (scrollRow + row) * COLUMNS + column;
        return index < visible.size() ? visible.get(index) : null;
    }

    private boolean inScrollbar(double mouseX, double mouseY) {
        return mouseX >= left + SCROLL_X && mouseX < left + SCROLL_X + SCROLL_W
                && mouseY >= top + GRID_Y && mouseY < top + GRID_Y + ROWS * CELL;
    }

    // ---- 描画 ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics);
        drawPanel(graphics, left, top, WIDTH, HEIGHT);
        graphics.drawString(font, title, left + 8, top + 6, TEXT, false);

        drawTabs(graphics);
        drawGrid(graphics, mouseX, mouseY);
        drawScrollbar(graphics);
        drawPreview(graphics);

        super.render(graphics, mouseX, mouseY, partialTicks);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        graphics.fill(x + 2, y + 2, x + w - 1, y + h - 1, PANEL_DARK);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
    }

    private void drawTabs(GuiGraphics graphics) {
        for (SignCategory category : SignCategory.values()) {
            int x = left + tabX(category);
            int y = top + TAB_Y;
            int w = tabWidth(category);
            boolean empty = catalog().byCategory(category).isEmpty();
            int background;
            int textColor;
            if (category == tab) {
                // 選択中だけ分類の色。黄色の W は黒い文字。P と F は同じ赤なので、F は文字を黄にして区別する
                // GHS は明るい赤、その他は黒に白の文字
                background = 0xFF000000 | category.getColor();
                textColor = category == SignCategory.W ? 0x1A1A1A : category == SignCategory.F ? 0xF9A800 : 0xFFFFFF;
            } else if (empty) {
                // 図柄がないタブは押せない表示
                background = TAB_DISABLED;
                textColor = 0x8B8B8B;
            } else {
                background = TAB_IDLE;
                textColor = 0xFFFFFF;
            }
            graphics.fill(x, y, x + w, y + TAB_H, background);
            Component label = tabLabel(category);
            graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, textColor, false);
        }
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int gridLeft = left + GRID_X;
        int gridTop = top + GRID_Y;
        graphics.fill(gridLeft - 1, gridTop - 1, gridLeft + COLUMNS * CELL + 1, gridTop + ROWS * CELL + 1, PANEL_DARK);
        if (visible.isEmpty()) {
            Component message = Component.translatable("gui.safetysigns.no_results");
            graphics.drawString(font, message, gridLeft + (COLUMNS * CELL - font.width(message)) / 2,
                    gridTop + ROWS * CELL / 2 - 4, 0xE0E0E0, false);
            return;
        }
        SignDefinition hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (scrollRow + row) * COLUMNS + column;
                if (index >= visible.size()) {
                    return;
                }
                SignDefinition definition = visible.get(index);
                int x = gridLeft + column * CELL;
                int y = gridTop + row * CELL;
                boolean isSelected = definition.getId().equals(selected);
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, isSelected ? 0xFFFFFFFF : SLOT);
                drawSign(graphics, SignTextures.textureOf(definition.getId()), x + 2, y + 2, 16, 16);
                if (definition == hovered) {
                    graphics.fill(x + 2, y + 2, x + 18, y + 18, 0x80FFFFFF);
                }
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int x = left + SCROLL_X;
        int y = top + GRID_Y;
        int trackHeight = ROWS * CELL;
        graphics.fill(x, y, x + SCROLL_W, y + trackHeight, PANEL_DARK);
        int max = maxScroll();
        int thumbHeight = max == 0 ? trackHeight : Math.max(8, trackHeight * ROWS / (ROWS + max));
        int thumbY = max == 0 ? y : y + (trackHeight - thumbHeight) * scrollRow / max;
        graphics.fill(x, thumbY, x + SCROLL_W, thumbY + thumbHeight, max == 0 ? SLOT : 0xFFDDDDDD);
    }

    private void drawPreview(GuiGraphics graphics) {
        // 看板の縦横比で、PREVIEW_SIZE の四角に収まる大きさ（2×2 は 1×1 と同じ正方形。縦長・横長の看板なら細くなる）
        int previewW = signWidth >= signHeight ? PREVIEW_SIZE : PREVIEW_SIZE * signWidth / signHeight;
        int previewH = signHeight >= signWidth ? PREVIEW_SIZE : PREVIEW_SIZE * signHeight / signWidth;
        int x = left + PREVIEW_X + (PREVIEW_W - previewW) / 2;
        int y = top + GRID_Y + (PREVIEW_SIZE - previewH) / 2;
        graphics.fill(x - 1, y - 1, x + previewW + 1, y + previewH + 1, PANEL_DARK);
        // 切り抜き OFF は、看板と同じく無地の下地の上に図柄を重ねる（プレビューは表だけ）
        if (!cutout && selected != null) {
            drawSign(graphics, SignTextures.BLANK, x, y, previewW, previewH);
        }
        drawSign(graphics, SignTextures.textureOf(selected), x, y, previewW, previewH);

        // コードと名前（夜間に光るかのボタンの下）
        int textY = top + GLOW_Y + 24;
        List<FormattedCharSequence> lines = new ArrayList<>();
        SignDefinition definition = catalog().get(selected);
        if (selected == null) {
            lines.add(Component.translatable("gui.safetysigns.blank").getVisualOrderText());
        } else if (definition == null) {
            lines.add(Component.literal("?").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("gui.safetysigns.unknown", selected), PREVIEW_W));
        } else {
            lines.add(Component.literal(definition.getCode()).getVisualOrderText());
            lines.addAll(font.split(Component.literal(definition.getName(SignTextures.language())), PREVIEW_W));
        }
        for (FormattedCharSequence line : lines) {
            if (textY + 9 > top + BOTTOM_Y - 2) {
                break;
            }
            graphics.drawString(font, line, left + PREVIEW_X + (PREVIEW_W - font.width(line)) / 2, textY, TEXT, false);
            textY += 10;
        }
    }

    /** 図柄を描く（看板と同じく、透明な所は板がない＝背景が見える。無地は無地の四角い板）。 */
    private static void drawSign(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        graphics.blit(x, y, 0, w, h, SignTextures.sprite(texture));
        RenderSystem.disableBlend();
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        SignDefinition hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderComponentTooltip(font, Arrays.asList(Component.literal(hovered.getCode()),
                    Component.literal(hovered.getName(SignTextures.language()))), mouseX, mouseY);
            return;
        }
        SignCategory hoveredTab = tabAt(mouseX, mouseY);
        if (hoveredTab != null) {
            graphics.renderComponentTooltip(font, Collections.singletonList(
                    Component.translatable("gui.safetysigns.category." + hoveredTab.name().toLowerCase(Locale.ROOT))), mouseX, mouseY);
        }
    }
}
