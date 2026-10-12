package net.drgmes.dwm.mixin;

import net.drgmes.dwm.compat.immersiveportals.ExteriorViewState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Distant Horizons' LODs out of the Exterior View's picture. Distant Horizons draws them from its own hook in the
 * world renderer, and the exterior is rendered through that same renderer from another camera in another dimension, so
 * the LODs of the player's own world were drawn into it. Pseudo and not required, so it costs nothing without Distant
 * Horizons, and a version with different method names just loses the effect.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class MixinDistantHorizonsClientApi {
    @Inject(method = "renderLods", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dwm$noLodsInExteriorView(CallbackInfo ci) {
        if (ExteriorViewState.rendering) ci.cancel();
    }

    @Inject(method = "renderDeferredLodsForShaders", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dwm$noDeferredLodsInExteriorView(CallbackInfo ci) {
        if (ExteriorViewState.rendering) ci.cancel();
    }

    @Inject(method = "renderFadeOpaque", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dwm$noOpaqueFadeInExteriorView(CallbackInfo ci) {
        if (ExteriorViewState.rendering) ci.cancel();
    }

    @Inject(method = "renderFadeTransparent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dwm$noTransparentFadeInExteriorView(CallbackInfo ci) {
        if (ExteriorViewState.rendering) ci.cancel();
    }
}
