package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.compat.iris.Iris;
import net.drgmes.dwm.setup.ModCompats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.Objects;

/**
 * The view out through a TARDIS's own door starts inside its shell, and stepping back from the door puts the
 * viewpoint behind the shell, so the shell would be drawn in the doorway. It is left out of any view made through
 * that TARDIS's own door. With a shader pack on, Iris also breaks Immersive Portals' clipping, so any shell right
 * at the far end of a portal view shows through it - those are left out too, but shells farther out (another
 * TARDIS parked down the street) are still drawn.
 */
public final class ImmersivePortalsShellCompat {
    private static final double SHADER_HIDE_RADIUS = 4.0;

    private ImmersivePortalsShellCompat() {
    }

    public static boolean shouldHideExteriorShell(String tardisId, BlockPos shellPos) {
        if (!ModCompats.immersivePortals() || !PortalRendering.isRendering()) return false;

        Portal renderingPortal = PortalRendering.getRenderingPortal();
        if (renderingPortal instanceof IMixinPortal portal && Objects.equals(portal.getTardisId(), tardisId)) return true;

        if (!ModCompats.iris() || !Iris.isShaderPackInUse() || renderingPortal == null) return false;
        return renderingPortal.getDestPos().squaredDistanceTo(Vec3d.ofCenter(shellPos)) <= SHADER_HIDE_RADIUS * SHADER_HIDE_RADIUS;
    }
}
