package net.drgmes.dwm.mixin;

import net.drgmes.dwm.compat.immersiveportals.ExteriorViewState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Stops Immersive Portals culling the Exterior View's picture around the TARDIS's own door portal. With Sodium, its
 * "outer frustum culling" skips everything hidden behind the nearest portal the camera is in front of, which for a
 * camera orbiting the TARDIS is its door portal - so once that portal's entity had loaded, most of the terrain
 * vanished from the view and left the sky and fog showing through.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.FrustumCuller", remap = false)
public class MixinFrustumCuller {
    @Inject(method = "getCurrentNearestVisibleCullablePortal", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void dwm$noPortalCullingInExteriorView(CallbackInfoReturnable<Portal> cir) {
        if (ExteriorViewState.rendering) cir.setReturnValue(null);
    }
}
