package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.compat.iris.Iris;
import net.drgmes.dwm.setup.ModCompats;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.Objects;

/**
 * The view out through a TARDIS's own door starts inside its shell, and stepping back from the door puts the
 * viewpoint behind the shell, so the shell would be drawn in the doorway. It is left out of any view made through
 * that TARDIS's own door - and, with a shader pack on, of every portal view, since Iris breaks Immersive Portals'
 * clipping and the shell shows through them.
 */
public final class ImmersivePortalsShellCompat {
    private ImmersivePortalsShellCompat() {
    }

    public static boolean shouldHideExteriorShell(String tardisId) {
        if (!ModCompats.immersivePortals() || !PortalRendering.isRendering()) return false;
        if (ModCompats.iris() && Iris.isShaderPackInUse()) return true;

        return PortalRendering.getRenderingPortal() instanceof IMixinPortal portal && Objects.equals(portal.getTardisId(), tardisId);
    }
}
