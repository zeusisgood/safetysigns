package io.github.zeusisgood.safetysigns.client.gui;

import io.github.zeusisgood.safetysigns.client.RecentSignsFile;
import io.github.zeusisgood.safetysigns.client.SignTextures;
import io.github.zeusisgood.safetysigns.common.SignAppearance;
import io.github.zeusisgood.safetysigns.common.SignCatalog;
import io.github.zeusisgood.safetysigns.common.SignCategory;
import io.github.zeusisgood.safetysigns.common.SignDefinition;
import io.github.zeusisgood.safetysigns.network.MessageSetSign;
import io.github.zeusisgood.safetysigns.network.SafetySignsNetwork;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 図柄選択の GUI。右上に閉じるボタン、上にタブ（最近・W P M E F GHS その他）、左に図柄のグリッド、右にプレビューとコード・名前、
 * 下に検索欄と「無地に戻す」、その下に設定（形・裏側・蓄光）の切り替え。
 * 図柄をクリック・設定を切り替えるたびにサーバーへ送る（決定ボタンなし）。コンテナを持たないので、クライアントだけで開く。
 */
public class GuiSignPicker extends GuiScreen {

    // 色（バニラ風の灰色パネル）
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int TEXT = 0x404040;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TAB_IDLE = 0xFF8B8B8B;
    private static final int TAB_DISABLED = 0xFFB5B5B5;
    private static final int TAB_HOVER = 0xFFA0A0A0;
    /** 選択中の「最近」のタブ（安全色と被らない濃い灰）。 */
    private static final int TAB_RECENT = 0xFF404040;
    /** 選んでいる図柄のマスの枠。 */
    private static final int SELECTED_FRAME = 0xFFF9A800;
    private static final int CLOSE_IDLE = 0xFF8B8B8B;
    private static final int CLOSE_HOVER = 0xFF9B2423;

    // 大きさと配置（パネルの左上から）
    private static final int WIDTH = 256;
    private static final int HEIGHT = 224;
    private static final int TAB_Y = 18;
    /** タブの幅の最小（1 文字の W P M E F の幅）。文字の長いタブ（GHS・その他）は文字に合わせて広げる。 */
    private static final int TAB_W = 24;
    private static final int TAB_GAP = 2;
    /** 文字の長いタブの、文字の左右の余白の合計。 */
    private static final int TAB_PADDING = 8;
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
    /** プレビューの下の、コードと名前。 */
    private static final int NAME_Y = GRID_Y + PREVIEW_SIZE + 6;
    private static final int BOTTOM_Y = 172;
    /** 2 段目（形・裏側・蓄光の切り替えを横に 3 つ）。 */
    private static final int OPTIONS_Y = BOTTOM_Y + 24;
    private static final int OPTION_W = 78;
    private static final int OPTION_GAP = 3;
    private static final int CLOSE_SIZE = 12;
    /** ツールチップを折り返す幅。 */
    private static final int TOOLTIP_W = 170;

    private static final int BUTTON_BLANK = 0;
    private static final int BUTTON_CUTOUT = 1;
    private static final int BUTTON_BACK = 2;
    private static final int BUTTON_GLOW = 3;
    private static final int BUTTON_CLOSE = 4;

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

    /** 選んでいるタブ。検索中・「最近」のタブは null。 */
    @Nullable
    private SignCategory tab;
    /** 「最近」のタブを選んでいるか（検索中は false）。 */
    private boolean recentTab;
    private List<SignDefinition> visible = Collections.emptyList();
    private int scrollRow;
    private boolean draggingScrollbar;

    private GuiTextField search;
    private GuiButton blankButton;
    private GuiButton cutoutButton;
    private GuiButton backButton;
    private GuiButton glowButton;
    private GuiButton closeButton;
    private int left;
    private int top;

    public GuiSignPicker(BlockPos pos, SignAppearance current, int signWidth, int signHeight) {
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
    public void initGui() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        Keyboard.enableRepeatEvents(true);

        String previousText = search != null ? search.getText() : "";
        search = new GuiTextField(1, fontRenderer, left + GRID_X + 1, top + BOTTOM_Y + 1, COLUMNS * CELL + SCROLL_W, 18);
        search.setMaxStringLength(64);
        search.setText(previousText);
        search.setFocused(true);

        buttonList.clear();
        blankButton = addButton(new GuiButton(BUTTON_BLANK, left + PREVIEW_X, top + BOTTOM_Y, PREVIEW_W, 20,
                I18n.format("gui.safetysigns.reset_blank")));
        cutoutButton = addButton(new GuiButton(BUTTON_CUTOUT, left + GRID_X, top + OPTIONS_Y, OPTION_W, 20, ""));
        backButton = addButton(new GuiButton(BUTTON_BACK, left + GRID_X + OPTION_W + OPTION_GAP, top + OPTIONS_Y, OPTION_W, 20, ""));
        glowButton = addButton(new GuiButton(BUTTON_GLOW, left + GRID_X + 2 * (OPTION_W + OPTION_GAP), top + OPTIONS_Y, OPTION_W, 20, ""));
        closeButton = addButton(new CloseButton(BUTTON_CLOSE, left + WIDTH - 8 - CLOSE_SIZE, top + 4));
        refreshOptionButtons();

        if (tab == null && !recentTab && previousText.isEmpty()) {
            // 最初は今の図柄のカテゴリ。無地・一覧にない図柄なら、図柄のある最初のカテゴリ
            SignDefinition current = catalog().get(selected);
            tab = current != null ? current.getCategory() : firstNonEmptyTab();
        }
        refreshVisible();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        // 看板が壊れた・離れたら閉じる（pos は代表のマス。どのマスが壊れても全体が消えるので、ここだけ見ればよい）
        if (!(mc.world.getTileEntity(pos) instanceof TileEntitySafetySign)
                || mc.player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MessageSetSign.MAX_DISTANCE_SQ) {
            mc.displayGuiScreen(null);
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
        String text = search.getText();
        if (!text.trim().isEmpty()) {
            visible = catalog().search(text);
        } else if (recentTab) {
            visible = recentDefinitions();
        } else if (tab != null) {
            visible = catalog().byCategory(tab);
        } else {
            visible = Collections.emptyList();
        }
        scrollRow = Math.min(scrollRow, maxScroll());
        blankButton.enabled = selected != null;
    }

    /** 最近使った図柄のうち、一覧にあるもの（新しい順）。一覧にない ID は履歴に残すが、ここには出さない。 */
    private List<SignDefinition> recentDefinitions() {
        List<SignDefinition> definitions = new ArrayList<>();
        for (String id : RecentSignsFile.get().getIds()) {
            SignDefinition definition = catalog().get(id);
            if (definition != null) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    /**
     * 形（切り抜き）・裏側・蓄光（夜間に光る）のボタンの文字と押せるか。状態を言葉で書き、蓄光の値だけ色を付ける。
     * 無地は形が四角しかないので、形は「標識」のまま押せない表示にする（蓄光は無地でも押せる）。
     */
    private void refreshOptionButtons() {
        cutoutButton.displayString = I18n.format("gui.safetysigns.cutout",
                I18n.format(cutout ? "gui.safetysigns.cutout.sign" : "gui.safetysigns.cutout.square"));
        cutoutButton.enabled = selected != null;
        backButton.displayString = I18n.format("gui.safetysigns.back",
                I18n.format(backPattern ? "gui.safetysigns.back.pattern" : "gui.safetysigns.back.silver"));
        glowButton.displayString = I18n.format("gui.safetysigns.glow", glow
                ? TextFormatting.GREEN + I18n.format("gui.safetysigns.glow.on")
                : TextFormatting.GRAY + I18n.format("gui.safetysigns.glow.off"));
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
        blankButton.enabled = selected != null;
        refreshOptionButtons();
        send();
        if (signId != null) {
            // 「最近」の並びは、タブを開き直すまで変えない（押した所の下で図柄が動かないように）
            RecentSignsFile.record(signId);
        }
    }

    /** 今の選択（図柄・切り抜き・裏面・光るか）をサーバーへ送る。サーバーが距離などを確かめてから書き込む。 */
    private void send() {
        SafetySignsNetwork.CHANNEL.sendToServer(new MessageSetSign(pos, new SignAppearance(selected, cutout, backPattern, glow)));
    }

    // ---- 入力 ----

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (search.textboxKeyTyped(typedChar, keyCode)) {
            onSearchChanged();
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private void onSearchChanged() {
        if (!search.getText().trim().isEmpty()) {
            // 検索中は全カテゴリ横断。タブの選択を外す
            tab = null;
            recentTab = false;
        } else if (tab == null && !recentTab) {
            SignDefinition current = catalog().get(selected);
            tab = current != null ? current.getCategory() : firstNonEmptyTab();
        }
        scrollRow = 0;
        refreshVisible();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        search.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 1 && inSearch(mouseX, mouseY)) {
            // 右クリックで検索の文字を消す（バニラのクリエイティブの検索と同じ）
            search.setText("");
            onSearchChanged();
            return;
        }
        if (mouseButton != 0) {
            return;
        }
        if (inRecentTab(mouseX, mouseY) && !recentDefinitions().isEmpty()) {
            search.setText("");
            tab = null;
            recentTab = true;
            scrollRow = 0;
            refreshVisible();
            playClick();
            return;
        }
        SignCategory clickedTab = tabAt(mouseX, mouseY);
        if (clickedTab != null && !catalog().byCategory(clickedTab).isEmpty()) {
            // タブを押したら検索をやめる
            search.setText("");
            tab = clickedTab;
            recentTab = false;
            scrollRow = 0;
            refreshVisible();
            playClick();
            return;
        }
        SignDefinition clicked = cellAt(mouseX, mouseY);
        if (clicked != null) {
            select(clicked.getId());
            return;
        }
        if (inScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollTo(mouseY);
        }
    }

    private void playClick() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingScrollbar) {
            scrollTo(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingScrollbar = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_BLANK) {
            select(null);
        } else if (button.id == BUTTON_CUTOUT) {
            cutout = !cutout;
            refreshOptionButtons();
            send();
        } else if (button.id == BUTTON_BACK) {
            backPattern = !backPattern;
            refreshOptionButtons();
            send();
        } else if (button.id == BUTTON_GLOW) {
            glow = !glow;
            refreshOptionButtons();
            send();
        } else if (button.id == BUTTON_CLOSE) {
            mc.displayGuiScreen(null);
        }
    }

    private void scrollTo(int mouseY) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        float ratio = (mouseY - (top + GRID_Y)) / (float) (ROWS * CELL);
        scrollRow = Math.max(0, Math.min(max, Math.round(ratio * max)));
    }

    @Nullable
    private SignCategory tabAt(int mouseX, int mouseY) {
        for (SignCategory category : SignCategory.values()) {
            int x = left + tabX(category);
            if (mouseX >= x && mouseX < x + tabWidth(category) && mouseY >= top + TAB_Y && mouseY < top + TAB_Y + TAB_H) {
                return category;
            }
        }
        return null;
    }

    private boolean inRecentTab(int mouseX, int mouseY) {
        int x = left + GRID_X;
        return mouseX >= x && mouseX < x + recentTabWidth() && mouseY >= top + TAB_Y && mouseY < top + TAB_Y + TAB_H;
    }

    private boolean inSearch(int mouseX, int mouseY) {
        return mouseX >= search.x && mouseX < search.x + search.getWidth() && mouseY >= search.y && mouseY < search.y + 18;
    }

    /** タブの文字。ISO 7010 の分類と GHS は分類の名前そのまま、その他は言語ファイルの文字。 */
    private static String tabLabel(SignCategory category) {
        return category == SignCategory.OTHER ? I18n.format("gui.safetysigns.tab.other") : category.name();
    }

    private int tabWidth(SignCategory category) {
        return Math.max(TAB_W, fontRenderer.getStringWidth(tabLabel(category)) + TAB_PADDING);
    }

    /** 「最近」のタブの幅（左端にある）。 */
    private int recentTabWidth() {
        return Math.max(TAB_W, fontRenderer.getStringWidth(I18n.format("gui.safetysigns.tab.recent")) + TAB_PADDING);
    }

    /** タブの左端（パネルの左から）。「最近」のタブと前のタブの幅を足していく。 */
    private int tabX(SignCategory category) {
        int x = GRID_X + recentTabWidth() + TAB_GAP;
        for (SignCategory before : SignCategory.values()) {
            if (before == category) {
                break;
            }
            x += tabWidth(before) + TAB_GAP;
        }
        return x;
    }

    @Nullable
    private SignDefinition cellAt(int mouseX, int mouseY) {
        int column = Math.floorDiv(mouseX - (left + GRID_X), CELL);
        int row = Math.floorDiv(mouseY - (top + GRID_Y), CELL);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            return null;
        }
        int index = (scrollRow + row) * COLUMNS + column;
        return index < visible.size() ? visible.get(index) : null;
    }

    private boolean inScrollbar(int mouseX, int mouseY) {
        return mouseX >= left + SCROLL_X && mouseX < left + SCROLL_X + SCROLL_W
                && mouseY >= top + GRID_Y && mouseY < top + GRID_Y + ROWS * CELL;
    }

    // ---- 描画 ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawPanel(left, top, WIDTH, HEIGHT);
        fontRenderer.drawString(I18n.format("gui.safetysigns.picker.title"), left + 8, top + 6, TEXT);

        drawTabs(mouseX, mouseY);
        drawGrid(mouseX, mouseY);
        drawScrollbar();
        drawPreview();

        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            fontRenderer.drawString(I18n.format("gui.safetysigns.search"), search.x + 4, search.y + 5, 0x808080);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawTooltips(mouseX, mouseY);
    }

    private void drawPanel(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        drawRect(x + 2, y + 2, x + w - 1, y + h - 1, PANEL_DARK);
        drawRect(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
    }

    private void drawTabs(int mouseX, int mouseY) {
        drawRecentTab(mouseX, mouseY);
        SignCategory hoveredTab = tabAt(mouseX, mouseY);
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
                background = category == hoveredTab ? TAB_HOVER : TAB_IDLE;
                textColor = 0xFFFFFF;
            }
            drawRect(x, y, x + w, y + TAB_H, background);
            String label = tabLabel(category);
            fontRenderer.drawString(label, x + (w - fontRenderer.getStringWidth(label)) / 2, y + 4, textColor);
        }
    }

    /** 「最近」のタブ。履歴の図柄がどれも一覧にないときは押せない表示。 */
    private void drawRecentTab(int mouseX, int mouseY) {
        int x = left + GRID_X;
        int y = top + TAB_Y;
        int w = recentTabWidth();
        int background;
        int textColor;
        if (recentTab) {
            background = TAB_RECENT;
            textColor = 0xFFFFFF;
        } else if (recentDefinitions().isEmpty()) {
            background = TAB_DISABLED;
            textColor = 0x8B8B8B;
        } else {
            background = inRecentTab(mouseX, mouseY) ? TAB_HOVER : TAB_IDLE;
            textColor = 0xFFFFFF;
        }
        drawRect(x, y, x + w, y + TAB_H, background);
        String label = I18n.format("gui.safetysigns.tab.recent");
        fontRenderer.drawString(label, x + (w - fontRenderer.getStringWidth(label)) / 2, y + 4, textColor);
    }

    private void drawGrid(int mouseX, int mouseY) {
        int gridLeft = left + GRID_X;
        int gridTop = top + GRID_Y;
        drawRect(gridLeft - 1, gridTop - 1, gridLeft + COLUMNS * CELL + 1, gridTop + ROWS * CELL + 1, PANEL_DARK);
        if (visible.isEmpty()) {
            String message = I18n.format("gui.safetysigns.no_results");
            fontRenderer.drawString(message, gridLeft + (COLUMNS * CELL - fontRenderer.getStringWidth(message)) / 2,
                    gridTop + ROWS * CELL / 2 - 4, 0xE0E0E0);
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
                if (definition.getId().equals(selected)) {
                    // 選んでいる図柄は、白い背景を黄の枠で囲む（図柄の白い部分と見分けるため）
                    drawRect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, SELECTED_FRAME);
                    drawRect(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xFFFFFFFF);
                } else {
                    drawRect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, SLOT);
                }
                drawSign(new ResourceLocation(definition.getTexture()), x + 2, y + 2, 16, 16);
                if (definition == hovered) {
                    drawRect(x + 2, y + 2, x + 18, y + 18, 0x80FFFFFF);
                }
            }
        }
    }

    private void drawScrollbar() {
        int x = left + SCROLL_X;
        int y = top + GRID_Y;
        int trackHeight = ROWS * CELL;
        drawRect(x, y, x + SCROLL_W, y + trackHeight, PANEL_DARK);
        int max = maxScroll();
        int thumbHeight = max == 0 ? trackHeight : Math.max(8, trackHeight * ROWS / (ROWS + max));
        int thumbY = max == 0 ? y : y + (trackHeight - thumbHeight) * scrollRow / max;
        drawRect(x, thumbY, x + SCROLL_W, thumbY + thumbHeight, max == 0 ? SLOT : 0xFFDDDDDD);
    }

    private void drawPreview() {
        // 看板の縦横比で、PREVIEW_SIZE の四角に収まる大きさ（2×2 は 1×1 と同じ正方形。縦長・横長の看板なら細くなる）
        int previewW = signWidth >= signHeight ? PREVIEW_SIZE : PREVIEW_SIZE * signWidth / signHeight;
        int previewH = signHeight >= signWidth ? PREVIEW_SIZE : PREVIEW_SIZE * signHeight / signWidth;
        int x = left + PREVIEW_X + (PREVIEW_W - previewW) / 2;
        int y = top + GRID_Y + (PREVIEW_SIZE - previewH) / 2;
        drawRect(x - 1, y - 1, x + previewW + 1, y + previewH + 1, PANEL_DARK);
        // 切り抜き OFF は、看板と同じく無地の下地の上に図柄を重ねる（プレビューは表だけ）
        if (!cutout && selected != null) {
            drawSign(SignTextures.BLANK, x, y, previewW, previewH);
        }
        drawSign(SignTextures.textureOf(selected), x, y, previewW, previewH);

        // コードと名前（プレビューの下。検索欄の上まで）
        int textY = top + NAME_Y;
        List<String> lines = new ArrayList<>();
        SignDefinition definition = catalog().get(selected);
        if (selected == null) {
            lines.add(I18n.format("gui.safetysigns.blank"));
        } else if (definition == null) {
            lines.add("?");
            lines.addAll(fontRenderer.listFormattedStringToWidth(I18n.format("gui.safetysigns.unknown", selected), PREVIEW_W));
        } else {
            lines.add(definition.getCode());
            lines.addAll(fontRenderer.listFormattedStringToWidth(definition.getName(SignTextures.language()), PREVIEW_W));
        }
        for (String line : lines) {
            if (textY + 9 > top + BOTTOM_Y - 2) {
                break;
            }
            fontRenderer.drawString(line, left + PREVIEW_X + (PREVIEW_W - fontRenderer.getStringWidth(line)) / 2, textY, TEXT);
            textY += 10;
        }
    }

    /** 図柄を描く（看板と同じく、透明な所は板がない＝背景が見える。無地は無地の四角い板）。 */
    private void drawSign(ResourceLocation texture, int x, int y, int w, int h) {
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        TextureAtlasSprite sprite = SignTextures.sprite(texture);
        drawTexturedModalRect(x, y, sprite, w, h);
        GlStateManager.disableBlend();
    }

    private void drawTooltips(int mouseX, int mouseY) {
        SignDefinition hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            drawHoveringText(Arrays.asList(hovered.getCode(), hovered.getName(SignTextures.language())), mouseX, mouseY);
            return;
        }
        if (inRecentTab(mouseX, mouseY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.safetysigns.tab.recent.tooltip")), mouseX, mouseY);
            return;
        }
        SignCategory hoveredTab = tabAt(mouseX, mouseY);
        if (hoveredTab != null) {
            drawHoveringText(Collections.singletonList(
                    I18n.format("gui.safetysigns.category." + hoveredTab.name().toLowerCase(Locale.ROOT))), mouseX, mouseY);
            return;
        }
        // ボタンの説明（押せない形のボタンにも、押せない理由を出す）
        String key = null;
        if (closeButton.isMouseOver()) {
            key = "gui.safetysigns.close";
        } else if (cutoutButton.isMouseOver()) {
            key = selected == null ? "gui.safetysigns.cutout.tooltip_blank" : "gui.safetysigns.cutout.tooltip";
        } else if (backButton.isMouseOver()) {
            key = "gui.safetysigns.back.tooltip";
        } else if (glowButton.isMouseOver()) {
            key = "gui.safetysigns.glow.tooltip";
        }
        if (key != null) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(I18n.format(key), TOOLTIP_W), mouseX, mouseY);
        }
    }

    /** 右上の閉じるボタン。普段は灰、マウスを乗せると赤の地に白い「×」。 */
    private static final class CloseButton extends GuiButton {

        CloseButton(int id, int x, int y) {
            super(id, x, y, CLOSE_SIZE, CLOSE_SIZE, "\u00d7");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            drawRect(x, y, x + width, y + height, hovered ? CLOSE_HOVER : CLOSE_IDLE);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, 0xFFFFFF);
        }
    }
}
