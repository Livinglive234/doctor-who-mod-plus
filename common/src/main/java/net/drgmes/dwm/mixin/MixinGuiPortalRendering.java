package net.drgmes.dwm.mixin;

import net.drgmes.dwm.compat.immersiveportals.ExteriorViewDiagnostics;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/**
 * Temporary: brackets Immersive Portals' GUI portal render so the Exterior View diagnostics can see the state of the
 * shader pack while the exterior is drawn. Pseudo and not required, so it costs nothing without Immersive Portals.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.GuiPortalRendering", remap = false)
public class MixinGuiPortalRendering {
    @Inject(method = "renderWorldIntoFrameBuffer", at = @At("HEAD"), remap = false, require = 0)
    private static void dwm$exteriorPassStart(WorldRenderInfo info, Framebuffer framebuffer, CallbackInfo ci) {
        ExteriorViewDiagnostics.exteriorPassStart();
    }

    @Inject(method = "renderWorldIntoFrameBuffer", at = @At("RETURN"), remap = false, require = 0)
    private static void dwm$exteriorPassEnd(WorldRenderInfo info, Framebuffer framebuffer, CallbackInfo ci) {
        ExteriorViewDiagnostics.exteriorPassEnd(framebuffer);
    }
}
