package net.drgmes.dwm.blocks.tardis.consoleunits.screens;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Vector2i;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public abstract class BaseTardisConsoleUnitMonitorScreen extends BaseTardisConsoleUnitScreen {
    public BaseTardisConsoleUnitMonitorScreen(Text title, BaseTardisConsoleUnitBlockEntity tardisConsoleUnitBlockEntity) {
        super(title, tardisConsoleUnitBlockEntity);
    }

    @Override
    public Identifier getBackground() {
        return DWM.TEXTURES.GUI.TARDIS.CONSOLE.MONITOR;
    }

    @Override
    public Vector2i getBackgroundOriginSize() {
        return DWM.TEXTURES.GUI.TARDIS.CONSOLE.MONITOR_SIZE;
    }

    // Shared by every monitor screen that greys out an owner/key-gated action: the owner id comes off this screen's
    // (client-side mirror of a) TardisStateManager, same as everything else the monitor renders.
    protected boolean isOwner() {
        if (this.client == null || this.client.player == null) return false;
        return TardisHelper.isOwner(this.ownerId(), this.client.player);
    }

    protected boolean hasOwnerOrKeyAccess(String tardisId) {
        if (this.client == null || this.client.player == null) return false;
        return TardisHelper.hasOwnerOrKeyAccess(this.ownerId(), tardisId, this.client.player);
    }

    private UUID ownerId() {
        return this.tardisConsoleUnitBlockEntity.tardisStateManager.getOwner();
    }
}
