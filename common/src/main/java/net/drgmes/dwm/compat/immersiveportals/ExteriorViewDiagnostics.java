package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.DWM;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Temporary logging for tracking down a native crash / washed-out picture with Iris shaders. Every line is prefixed
 * "[ExteriorView]" in latest.log. The first frames are all logged (a native crash loses whatever came after the last
 * line), then one line every couple of seconds.
 */
@Environment(EnvType.CLIENT)
public final class ExteriorViewDiagnostics {
    private static final int VERBOSE_FRAMES = 40;
    private static final long INTERVAL_MS = 2000L;

    private static int frame;
    private static long lastLog;

    private ExteriorViewDiagnostics() {
    }

    public static void opened(String dimension, boolean following) {
        frame = 0;
        lastLog = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        Window w = client.getWindow();
        log("OPEN dimension=" + dimension + " following=" + following
            + " shaderPack=" + shaderPackInUse() + " window=" + w.getFramebufferWidth() + "x" + w.getFramebufferHeight()
            + " viewDistance=" + client.options.getClampedViewDistance()
            + " glRenderer=" + GL11.glGetString(GL11.GL_RENDERER) + " glVersion=" + GL11.glGetString(GL11.GL_VERSION));
        glError("open");
    }

    public static void closed(String why) {
        log("CLOSE " + why);
    }

    /** Called right before the frame is handed to Immersive Portals. */
    public static void beforeSubmit(Object world, Framebuffer fb, Object cameraPos, int renderDistance) {
        if (!shouldLog()) return;
        log("frame " + frame + " submit world=" + (world == null ? "NULL" : "ok") + " camera=" + cameraPos
            + " renderDist=" + renderDistance + " fb=" + describe(fb) + " shaderPack=" + shaderPackInUse()
            + " boundFbo=" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING));
        glError("beforeSubmit");
    }

    /** Called after the picture has been drawn to the screen. */
    public static void afterDraw(Framebuffer fb) {
        if (frame < VERBOSE_FRAMES || System.currentTimeMillis() - lastLog >= INTERVAL_MS) {
            glError("afterDraw");
            if (fb != null) {
                int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fb.fbo);
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) log("frame " + frame + " framebuffer INCOMPLETE status=0x" + Integer.toHexString(status));
            }
        }
        frame++;
        if (frame >= VERBOSE_FRAMES) lastLog = System.currentTimeMillis();
    }

    private static boolean shouldLog() {
        return frame < VERBOSE_FRAMES || System.currentTimeMillis() - lastLog >= INTERVAL_MS;
    }

    private static String describe(Framebuffer fb) {
        if (fb == null) return "NULL";
        return fb.textureWidth + "x" + fb.textureHeight + " fbo=" + fb.fbo + " color=" + fb.getColorAttachment() + " depth=" + fb.getDepthAttachment();
    }

    private static void glError(String where) {
        int err;
        while ((err = GL11.glGetError()) != GL11.GL_NO_ERROR) log("GL ERROR 0x" + Integer.toHexString(err) + " at " + where + " (frame " + frame + ")");
    }

    private static String shaderPackInUse() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            return String.valueOf(api.getClass().getMethod("isShaderPackInUse").invoke(api));
        }
        catch (Throwable t) {
            return "n/a(" + t.getClass().getSimpleName() + ")";
        }
    }

    private static void log(String message) {
        DWM.LOGGER.info("[ExteriorView] {}", message);
    }
}
