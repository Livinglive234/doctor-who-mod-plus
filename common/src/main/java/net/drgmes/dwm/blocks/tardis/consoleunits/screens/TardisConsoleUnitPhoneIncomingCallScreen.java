package net.drgmes.dwm.blocks.tardis.consoleunits.screens;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.network.server.TardisPhoneAnswerPacket;
import net.drgmes.dwm.network.server.TardisPhoneDeclinePacket;
import net.drgmes.dwm.utils.base.screens.elements.BaseButton;
import net.drgmes.dwm.utils.helpers.RenderHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;
import org.joml.Vector2i;

// Right-clicking a ringing phone used to answer it immediately - this shows who's calling first, so answering
// (or declining) is a deliberate choice instead of a reflex. Sneaking on the control still declines instantly,
// without opening this at all.
@Environment(EnvType.CLIENT)
public class TardisConsoleUnitPhoneIncomingCallScreen extends BaseTardisConsoleUnitScreen {
    private final String callerName;

    private ButtonWidget answerButton;
    private ButtonWidget declineButton;

    public TardisConsoleUnitPhoneIncomingCallScreen(BaseTardisConsoleUnitBlockEntity tardisConsoleUnitBlockEntity, String callerName) {
        super(DWM.TEXTS.PHONE_DIAL_TITLE, tardisConsoleUnitBlockEntity);
        this.callerName = callerName;
    }

    @Override
    public boolean shouldCloseOnInventoryKey() {
        return true;
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
    public void init() {
        super.init();

        Vector2i answerButtonPos = this.getRightBottomRenderPos(BUTTON_SIZE + SCREEN_MARGIN, BUTTON_SIZE + SCREEN_MARGIN);
        this.answerButton = new BaseButton(answerButtonPos, BUTTON_SIZE, BUTTON_PADDING, DWM.TEXTS.PHONE_CALL_ANSWER, DWM.TEXTURES.GUI.COMMON.ELEMENTS.ACCEPT, (b) -> this.apply());

        Vector2i declineButtonPos = this.getLeftBottomRenderPos(SCREEN_MARGIN, BUTTON_SIZE + SCREEN_MARGIN);
        this.declineButton = new BaseButton(declineButtonPos, BUTTON_SIZE, BUTTON_PADDING, DWM.TEXTS.PHONE_CALL_DECLINE, DWM.TEXTURES.GUI.COMMON.ELEMENTS.CANCEL, (b) -> this.decline());

        this.addDrawableChild(this.answerButton);
        this.addDrawableChild(this.declineButton);
    }

    @Override
    public void renderAdditional(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderAdditional(context, mouseX, mouseY, delta);

        int maxWidth = this.getBackgroundSize().x - this.getBackgroundBorderSize().x * 2;
        int textX = (int) Math.floor(this.getBackgroundSize().x / 2F);
        int textY = (int) Math.floor(this.getBackgroundSize().y / 2F) - BUTTON_SIZE;
        Vector2i textPos = this.getRenderPos(textX, textY);

        RenderHelper.drawTextMultilineCentered(DWM.TEXTS.PHONE_CALL_STATE_RINGING.apply(this.callerName), this.textRenderer, context, textPos, this.textRenderer.fontHeight, maxWidth, 0xE0E0E0);
    }

    @Override
    protected void apply() {
        new TardisPhoneAnswerPacket().sendToServer();
        super.apply();
    }

    private void decline() {
        new TardisPhoneDeclinePacket().sendToServer();
        this.close();
    }
}
