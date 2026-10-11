package net.drgmes.dwm.mixin;

import net.drgmes.dwm.compat.immersiveportals.ExteriorViewDiagnostics;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary: lets the Exterior View diagnostics see the state before and after each world is rendered. */
@Mixin(GameRenderer.class)
public class MixinGameRendererDiagnostics {
    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void dwm$worldRenderStart(RenderTickCounter tickCounter, CallbackInfo ci) {
        ExteriorViewDiagnostics.worldRenderStart();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"), require = 0)
    private void dwm$worldRenderEnd(RenderTickCounter tickCounter, CallbackInfo ci) {
        ExteriorViewDiagnostics.worldRenderEnd();
    }
}
