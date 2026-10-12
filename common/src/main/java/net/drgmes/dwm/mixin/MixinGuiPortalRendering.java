package net.drgmes.dwm.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.drgmes.dwm.compat.immersiveportals.ExteriorViewState;
import net.drgmes.dwm.compat.immersiveportals.IrisFramebufferVersions;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/**
 * Brackets Immersive Portals' GUI portal render, so the rest of the mod knows when the Exterior View's own picture is
 * being drawn, and puts the GPU in a known state first. Pseudo and not required, so it costs nothing without Immersive Portals.
 */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.render.GuiPortalRendering", remap = false)
public class MixinGuiPortalRendering {
    @Inject(method = "renderWorldIntoFrameBuffer", at = @At("HEAD"), remap = false, require = 0)
    private static void dwm$exteriorPassStart(WorldRenderInfo info, Framebuffer framebuffer, CallbackInfo ci) {
        ExteriorViewState.rendering = ExteriorViewState.open;
        IrisFramebufferVersions.separate(framebuffer);

        // The pass runs right after the game has drawn its screens, and a screen's drawing leaves its own GPU settings
        // behind (depth writes, blending, the scissor box, the active texture unit...). The game's own world render never
        // starts from that, and a shader pack's pipeline does not expect it: with one on, the swaying plants and the water
        // came out flat white. Start the pass from the state a world render starts from.
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.disableScissor();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disablePolygonOffset();
    }

    @Inject(method = "renderWorldIntoFrameBuffer", at = @At("RETURN"), remap = false, require = 0)
    private static void dwm$exteriorPassEnd(WorldRenderInfo info, Framebuffer framebuffer, CallbackInfo ci) {
        ExteriorViewState.rendering = false;
    }
}
