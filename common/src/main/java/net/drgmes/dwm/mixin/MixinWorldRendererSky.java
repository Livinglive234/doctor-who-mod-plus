package net.drgmes.dwm.mixin;

import net.drgmes.dwm.compat.immersiveportals.ExteriorViewState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the sky out of the Exterior View's picture with a shader pack on. The view asks Immersive Portals not to draw
 * the sky (it washes the whole picture out), but Immersive Portals only honours that without shaders - so with shaders
 * the sky was drawn and washed the picture out all over again.
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRendererSky {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void dwm$noSkyInExteriorView(Matrix4f modelView, Matrix4f projection, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (ExteriorViewState.rendering) ci.cancel();
    }
}
