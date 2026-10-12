package io.github.zeusisgood.safetysigns.client.gui;

import io.github.zeusisgood.safetysigns.client.SignTextures;
import io.github.zeusisgood.safetysigns.common.SignCatalog;
import io.github.zeusisgood.safetysigns.common.SignCategory;
import io.github.zeusisgood.safetysigns.common.SignDefinition;
import io.github.zeusisgood.safetysigns.network.MessageSetSign;
import io.github.zeusisgood.safetysigns.network.SafetySignsNetwork;
import io.github.zeusisgood.safetysigns.tile.TileEntitySafetySign;
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
 * 図柄選択の GUI。上にカテゴリのタブ、左に図柄のグリッド、右にプレビュー、下に検索欄と「無地に戻す」。
 * 図柄をクリックするたびにサーバーへ送る（決定ボタンなし）。コンテナを持たないので、クライアントだけで開く。
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

    // 大きさと配置（パネルの左上から）
    private static final int WIDTH = 256;
    private static final int HEIGHT = 200;
    private static final int TAB_Y = 18;
    private static final int TAB_W = 24;
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
    private static final int BOTTOM_Y = 172;

    private static final int BUTTON_BLANK = 0;

    private final BlockPos pos;
    /** GUI で選んでいる図柄 ID（null は無地）。サーバーへ送った値。 */
    @Nullable
    private String selected;

    /** 選んでいるタブ。検索中は null（全カテゴリ横断）。 */
    @Nullable
    private SignCategory tab;
    private List<SignDefinition> visible = Collections.emptyList();
    private int scrollRow;
    private boolean draggingScrollbar;

    private GuiTextField search;
    private GuiButton blankButton;
    private int left;
    private int top;

    public GuiSignPicker(BlockPos pos, @Nullable String currentSignId) {
        this.pos = pos;
        this.selected = currentSignId;
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

        if (tab == null && previousText.isEmpty()) {
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
        // 看板が壊れた・離れたら閉じる
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
        } else if (tab != null) {
            visible = catalog().byCategory(tab);
        } else {
            visible = Collections.emptyList();
        }
        scrollRow = Math.min(scrollRow, maxScroll());
        blankButton.enabled = selected != null;
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
        blankButton.enabled = selected != null;
        SafetySignsNetwork.CHANNEL.sendToServer(new MessageSetSign(pos, signId));
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
        } else if (tab == null) {
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
        if (mouseButton != 0) {
            return;
        }
        SignCategory clickedTab = tabAt(mouseX, mouseY);
        if (clickedTab != null && !catalog().byCategory(clickedTab).isEmpty()) {
            // タブを押したら検索をやめる
            search.setText("");
            tab = clickedTab;
            scrollRow = 0;
            refreshVisible();
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= top + TAB_Y && mouseY < top + TAB_Y + TAB_H) {
                return category;
            }
        }
        return null;
    }

    private static int tabX(SignCategory category) {
        return GRID_X + category.ordinal() * (TAB_W + 2);
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

        drawTabs();
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

    private void drawTabs() {
        for (SignCategory category : SignCategory.values()) {
            int x = left + tabX(category);
            int y = top + TAB_Y;
            boolean empty = catalog().byCategory(category).isEmpty();
            int background;
            int textColor;
            if (category == tab) {
                // 選択中だけ安全色。黄色の W は黒い文字。P と F は同じ赤なので、F は文字を黄にして区別する
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
            drawRect(x, y, x + TAB_W, y + TAB_H, background);
            String letter = category.name();
            fontRenderer.drawString(letter, x + (TAB_W - fontRenderer.getStringWidth(letter)) / 2, y + 4, textColor);
        }
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
                boolean isSelected = definition.getId().equals(selected);
                drawRect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, isSelected ? 0xFFFFFFFF : SLOT);
                drawSign(new ResourceLocation(definition.getTexture()), x + 2, y + 2, 16);
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
        int x = left + PREVIEW_X + (PREVIEW_W - PREVIEW_SIZE) / 2;
        int y = top + GRID_Y;
        drawRect(x - 1, y - 1, x + PREVIEW_SIZE + 1, y + PREVIEW_SIZE + 1, PANEL_DARK);
        drawSign(SignTextures.textureOf(selected), x, y, PREVIEW_SIZE);

        // コードと名前
        int textY = y + PREVIEW_SIZE + 6;
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

    /** 無地の板の上に図柄を重ねて描く（図柄の透明な所から板が見えるように、看板と同じ重ね方）。 */
    private void drawSign(ResourceLocation texture, int x, int y, int size) {
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        drawTexturedModalRect(x, y, SignTextures.sprite(SignTextures.BLANK), size, size);
        if (!texture.equals(SignTextures.BLANK)) {
            TextureAtlasSprite sprite = SignTextures.sprite(texture);
            drawTexturedModalRect(x, y, sprite, size, size);
        }
        GlStateManager.disableBlend();
    }

    private void drawTooltips(int mouseX, int mouseY) {
        SignDefinition hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            drawHoveringText(Arrays.asList(hovered.getCode(), hovered.getName(SignTextures.language())), mouseX, mouseY);
            return;
        }
        SignCategory hoveredTab = tabAt(mouseX, mouseY);
        if (hoveredTab != null) {
            drawHoveringText(Collections.singletonList(
                    I18n.format("gui.safetysigns.category." + hoveredTab.name().toLowerCase(Locale.ROOT))), mouseX, mouseY);
        }
    }
}
