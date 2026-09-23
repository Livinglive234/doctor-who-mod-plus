package net.drgmes.dwm.blocks.tardis.consoleunits.screens;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneManager;
import net.drgmes.dwm.network.server.TardisPhoneDialPacket;
import net.drgmes.dwm.utils.base.screens.BaseListWidget;
import net.drgmes.dwm.utils.base.screens.elements.BaseButton;
import net.drgmes.dwm.utils.helpers.RenderHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;
import org.joml.Vector2i;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

// Same list-a-thing-then-accept/cancel shape as TardisConsoleUnitTelepathicInterfaceLocationsScreen, with TARDISes
// (by owner name) in place of biomes/structures.
@Environment(EnvType.CLIENT)
public class TardisConsoleUnitPhoneDialScreen extends BaseTardisConsoleUnitScreen {
    private final List<TardisPhoneManager.DialEntry> entries;
    private List<TardisPhoneManager.DialEntry> filteredEntries;

    private boolean isInited;

    private TextFieldWidget searchField;
    private String lastSearch;

    private DialListWidget dialListWidget;
    private DialListWidget.DialEntryWidget selected = null;

    private ButtonWidget callButton;
    private ButtonWidget cancelButton;

    public TardisConsoleUnitPhoneDialScreen(BaseTardisConsoleUnitBlockEntity tardisConsoleUnitBlockEntity, List<TardisPhoneManager.DialEntry> entries) {
        super(DWM.TEXTS.PHONE_DIAL_TITLE, tardisConsoleUnitBlockEntity);

        this.entries = Collections.unmodifiableList(entries);
        this.filteredEntries = this.entries;
    }

    @Override
    public Identifier getBackground() {
        return DWM.TEXTURES.GUI.TARDIS.CONSOLE.TELEPATHIC_INTERFACE;
    }

    @Override
    public Vector2i getBackgroundOriginSize() {
        return DWM.TEXTURES.GUI.TARDIS.CONSOLE.TELEPATHIC_INTERFACE_SIZE;
    }

    @Override
    public boolean shouldCloseOnInventoryKey() {
        return !this.searchField.isFocused();
    }

    @Override
    public void init() {
        super.init();

        this.dialListWidget = new DialListWidget(this, this.getDialListPos(), this.getDialListSize());

        Vector2i searchFieldPos = this.getLeftTopRenderPos(1, 1);
        this.searchField = new TextFieldWidget(this.textRenderer, searchFieldPos.x, searchFieldPos.y, this.getBackgroundSize().x - this.getBackgroundBorderSize().x * 2 - 2, INPUT_HEIGHT, DWM.TEXTS.PHONE_DIAL_SEARCH);

        Vector2i callButtonPos = this.getRightBottomRenderPos(BUTTON_SIZE + SCREEN_MARGIN, BUTTON_SIZE + SCREEN_MARGIN);
        this.callButton = new BaseButton(callButtonPos, BUTTON_SIZE, BUTTON_PADDING, DWM.TEXTS.PHONE_DIAL_CALL, DWM.TEXTURES.GUI.COMMON.ELEMENTS.ACCEPT, (b) -> this.apply());

        Vector2i cancelButtonPos = this.getLeftBottomRenderPos(SCREEN_MARGIN, BUTTON_SIZE + SCREEN_MARGIN);
        this.cancelButton = new BaseButton(cancelButtonPos, BUTTON_SIZE, BUTTON_PADDING, DWM.TEXTS.PHONE_DIAL_CANCEL, DWM.TEXTURES.GUI.COMMON.ELEMENTS.CANCEL, (b) -> this.back());

        this.addDrawableChild(this.dialListWidget);
        this.addDrawableChild(this.callButton);
        this.addDrawableChild(this.cancelButton);

        if (this.entries.isEmpty()) {
            this.addDrawable(this.searchField);
        }
        else {
            this.addDrawableChild(this.searchField);
            this.setInitialFocus(this.searchField);
        }

        this.isInited = true;
        this.update();
    }

    @Override
    public void resize(MinecraftClient mc, int width, int height) {
        String search = this.searchField.getText();
        DialListWidget.DialEntryWidget selected = this.selected;

        super.resize(mc, width, height);
        this.searchField.setText(search);
        this.selected = selected;

        if (!this.searchField.getText().isEmpty()) {
            this.reloadEntries();
        }
    }

    @Override
    public void tick() {
        this.dialListWidget.setSelected(this.selected);

        if (!this.searchField.getText().equals(this.lastSearch)) {
            this.selected = null;
            this.reloadEntries();
            this.dialListWidget.refreshList();
            this.update();
        }
    }

    @Override
    protected void apply() {
        if (this.selected != null) new TardisPhoneDialPacket(this.selected.entry.tardisId()).sendToServer();
        super.apply();
    }

    @Override
    public void renderAdditional(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderAdditional(context, mouseX, mouseY, delta);
        if (!this.entries.isEmpty()) return;

        int maxWidth = this.getBackgroundSize().x - this.getBackgroundBorderSize().x * 2;
        int textX = (int) Math.floor(this.getBackgroundSize().x / 2F);
        int textY = (int) Math.floor(this.getBackgroundSize().y / 2F) - BUTTON_SIZE;
        Vector2i textPos = this.getRenderPos(textX, textY);

        RenderHelper.drawTextMultilineCentered(DWM.TEXTS.PHONE_DIAL_EMPTY.copy().formatted(Formatting.GRAY, Formatting.ITALIC), this.textRenderer, context, textPos, this.textRenderer.fontHeight + 2, maxWidth, 0xE0E0E0);
    }

    private boolean hasSearch() {
        return this.searchField != null && !Objects.equals(this.searchField.getText(), "");
    }

    private Vector2i getDialListPos() {
        return this.getLeftTopRenderPos(0, INPUT_HEIGHT + 4);
    }

    private Vector2i getDialListSize() {
        return new Vector2i(this.getBackgroundSize().x - this.getBackgroundBorderSize().x * 2, this.getBackgroundSize().y - this.getBackgroundBorderSize().y * 2 - SCREEN_MARGIN * 2 - INPUT_HEIGHT - BUTTON_SIZE - 6);
    }

    private void update() {
        if (!this.isInited) return;

        this.lastSearch = this.searchField.getText();
        this.callButton.active = this.selected != null;
    }

    private void setSelected(DialListWidget.DialEntryWidget entry) {
        this.selected = entry == this.selected ? null : entry;
        this.update();
    }

    private void reloadEntries() {
        if (this.hasSearch()) {
            this.lastSearch = this.searchField.getText();
            if (this.lastSearch == null) this.lastSearch = "";
            String search = this.lastSearch.toLowerCase();

            this.filteredEntries = this.entries.stream().filter((entry) -> entry.ownerName().toLowerCase().contains(search)).toList();
            return;
        }

        this.filteredEntries = this.entries;
    }

    private static class DialListWidget extends BaseListWidget {
        private final TardisConsoleUnitPhoneDialScreen parent;

        public DialListWidget(TardisConsoleUnitPhoneDialScreen parent, Vector2i pos, Vector2i size) {
            super(parent.client, pos, size, LINE_HEIGHT);
            this.parent = parent;
            this.init();
        }

        @Override
        public Vector2f getScale() {
            return this.parent.cachedScale;
        }

        @Override
        public void refreshList() {
            super.refreshList();
            this.parent.filteredEntries.forEach((entry) -> this.addEntry(new DialEntryWidget(entry)));
        }

        private class DialEntryWidget extends BaseListEntry {
            private final TardisPhoneManager.DialEntry entry;

            public DialEntryWidget(TardisPhoneManager.DialEntry entry) {
                this.entry = entry;
            }

            @Override
            public Text getText() {
                return DWM.TEXTS.PHONE_DIAL_ENTRY.apply(this.entry.ownerName()).copy().formatted(Formatting.WHITE);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!super.mouseClicked(mouseX, mouseY, button)) return false;
                DialListWidget.this.parent.setSelected(this);
                return true;
            }
        }
    }
}
