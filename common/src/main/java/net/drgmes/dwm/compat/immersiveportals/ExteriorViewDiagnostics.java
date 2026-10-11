package net.drgmes.dwm.compat.immersiveportals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.drgmes.dwm.DWM;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;

/**
 * Temporary logging for tracking down a native crash / washed-out picture with Iris shaders. Every line is prefixed
 * "[ExteriorView]" in latest.log. The first frames are all logged (a native crash loses whatever came after the last
 * line), then one line every couple of seconds. On "deep" frames (the first few, then once a second) it also logs the
 * shader pack's global state as seen by the player's own render and by the exterior render, and samples the picture's
 * pixels, so a drift in either can be seen against time.
 */
@Environment(EnvType.CLIENT)
public final class ExteriorViewDiagnostics {
    private static final int VERBOSE_FRAMES = 40;
    private static final long INTERVAL_MS = 2000L;
    private static final int DEEP_FRAMES = 4;
    private static final long DEEP_INTERVAL_MS = 1000L;

    private static int frame;
    private static long lastLog;
    private static long lastDeep;
    private static boolean loggingThisFrame;
    private static boolean deepThisFrame;
    private static boolean inExteriorPass;

    private ExteriorViewDiagnostics() {
    }

    public static void opened(String dimension, boolean following) {
        frame = 0;
        lastLog = 0;
        lastDeep = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        Window w = client.getWindow();
        log("OPEN dimension=" + dimension + " following=" + following
            + " shaderPack=" + shaderPackInUse() + " window=" + w.getFramebufferWidth() + "x" + w.getFramebufferHeight()
            + " viewDistance=" + client.options.getClampedViewDistance()
            + " glRenderer=" + GL11.glGetString(GL11.GL_RENDERER) + " glVersion=" + GL11.glGetString(GL11.GL_VERSION));
        log("OPEN pack=" + staticCall("net.irisshaders.iris.Iris", "getCurrentPackName")
            + " ipRenderer=" + staticField("qouteall.imm_ptl.core.IPCGlobal", "renderer"));
        glError("open");
    }

    public static void closed(String why) {
        log("CLOSE " + why);
    }

    /** Called right before the frame is handed to Immersive Portals. */
    public static void beforeSubmit(Object world, Framebuffer fb, Object cameraPos, int renderDistance) {
        loggingThisFrame = shouldLog();
        long now = System.currentTimeMillis();
        deepThisFrame = frame < DEEP_FRAMES || now - lastDeep >= DEEP_INTERVAL_MS;
        if (deepThisFrame) lastDeep = now;
        if (!loggingThisFrame && !deepThisFrame) return;

        if (loggingThisFrame) {
            lastLog = now;
            log("frame " + frame + " submit world=" + (world == null ? "NULL" : "ok") + " camera=" + cameraPos
                + " renderDist=" + renderDistance + " fb=" + describe(fb) + " shaderPack=" + shaderPackInUse()
                + " boundFbo=" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING));
            glError("beforeSubmit");
        }

        snapshot("gui-main");
    }

    /** Called after the picture has been drawn to the screen. */
    public static void afterDraw(Framebuffer fb) {
        if (loggingThisFrame) {
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
    }

    // --- hooks called from the mixins -------------------------------------------------------------------------------

    /** Immersive Portals is about to render the exterior into the screen's framebuffer. */
    public static void exteriorPassStart() {
        inExteriorPass = true;
        IrisStateIsolation.enter();
        snapshot("ext-start");
    }

    /** Immersive Portals has finished rendering the exterior into the framebuffer. */
    public static void exteriorPassEnd(Framebuffer fb) {
        inExteriorPass = false;
        IrisStateIsolation.leave();
        snapshot("ext-end");
        if (deepThisFrame) samplePixels(fb);
    }

    /** The game renderer is about to render a world (the player's own, or the exterior inside the pass). */
    public static void worldRenderStart() {
        snapshot(inExteriorPass ? "ext-world-pre" : "main-world-pre");
    }

    /** The game renderer has just rendered a world. */
    public static void worldRenderEnd() {
        snapshot(inExteriorPass ? "ext-world-post" : "main-world-post");
    }

    // ----------------------------------------------------------------------------------------------------------------

    private static boolean shouldLog() {
        return frame < VERBOSE_FRAMES || System.currentTimeMillis() - lastLog >= INTERVAL_MS;
    }

    private static void snapshot(String label) {
        if (!deepThisFrame || !ExteriorViewState.open) return;

        MinecraftClient client = MinecraftClient.getInstance();
        StringBuilder sb = new StringBuilder("frame ").append(frame).append(" STATE ").append(label);

        try {
            ClientWorld world = client.world;
            if (world != null) {
                sb.append(" world=").append(world.getRegistryKey().getValue())
                    .append(" timeOfDay=").append(world.getTimeOfDay())
                    .append(" skyAngle=").append(fmt(world.getSkyAngle(1F)))
                    .append(" rain=").append(fmt(world.getRainGradient(1F)))
                    .append(" thunder=").append(fmt(world.getThunderGradient(1F)))
                    .append(" skyLight=").append(world.getDimension().hasSkyLight())
                    .append(" ambient=").append(fmt(world.getDimension().ambientLight()));
            }

            Entity camera = client.getCameraEntity();
            if (camera != null) {
                BlockPos pos = camera.getBlockPos();
                sb.append(" camEntity=").append(camera.getWorld().getRegistryKey().getValue()).append('@').append(pos.toShortString())
                    .append(" entSky/block=").append(camera.getWorld().getLightLevel(LightType.SKY, pos)).append('/').append(camera.getWorld().getLightLevel(LightType.BLOCK, pos));
            }

            sb.append(" gcCamera=").append(client.gameRenderer.getCamera().getPos());
            sb.append(" fogStart/End=").append(fmt(RenderSystem.getShaderFogStart())).append('/').append(fmt(RenderSystem.getShaderFogEnd()))
                .append(" fogColor=").append(java.util.Arrays.toString(RenderSystem.getShaderFogColor()));
        }
        catch (Throwable t) {
            sb.append(" worldErr=").append(t);
        }

        sb.append(" | iris:");
        try {
            Class<?> state = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            Object instance = state.getField("INSTANCE").get(null);
            sb.append(" fog=").append(call(instance, "getFogColor")).append(" fogDensity=").append(call(instance, "getFogDensity"))
                .append(" tick=").append(call(instance, "getTickDelta")).append(" cloudTime=").append(call(instance, "getCloudTime"))
                .append(" darkness=").append(call(instance, "getDarknessLightFactor"))
                .append(" mv=").append(String.valueOf(call(instance, "getGbufferModelView")).replaceAll("\\s+", " "));
        }
        catch (Throwable t) {
            sb.append(" stateErr=").append(t.getClass().getSimpleName());
        }

        sb.append(" frameCounter=").append(staticFieldCall("net.irisshaders.iris.uniforms.SystemTimeUniforms", "COUNTER", "getAsInt"))
            .append(" frameTime=").append(staticFieldCall("net.irisshaders.iris.uniforms.SystemTimeUniforms", "TIMER", "getFrameTimeCounter"))
            .append(" lastFrame=").append(staticFieldCall("net.irisshaders.iris.uniforms.SystemTimeUniforms", "TIMER", "getLastFrameTime"))
            .append(" camPos=").append(staticCall("net.irisshaders.iris.uniforms.CameraUniforms", "getUnshiftedCameraPosition"));

        try {
            Object manager = staticCall("net.irisshaders.iris.Iris", "getPipelineManager");
            Object pipeline = manager == null ? null : call(manager, "getPipelineNullable");
            sb.append(" pipeline=").append(pipeline == null ? "null" : pipeline.getClass().getSimpleName() + "#" + Integer.toHexString(System.identityHashCode(pipeline)));
        }
        catch (Throwable t) {
            sb.append(" pipelineErr=").append(t.getClass().getSimpleName());
        }

        sb.append(" | gl: viewport=").append(java.util.Arrays.toString(glInts(GL11.GL_VIEWPORT, 4)))
            .append(" drawFbo=").append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING))
            .append(" readFbo=").append(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING))
            .append(" program=").append(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM))
            .append(" blend=").append(GL11.glIsEnabled(GL11.GL_BLEND))
            .append(" depth=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
            .append(" cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE));

        log(sb.toString());
    }

    // A grid of single pixels from the exterior's framebuffer: how white and how opaque the picture is right now.
    private static void samplePixels(Framebuffer fb) {
        if (fb == null) return;

        try {
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fb.fbo);

            FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
            double r = 0, g = 0, b = 0, a = 0, minLum = 1, maxLum = 0;
            int n = 0;

            for (int ix = 1; ix <= 5; ix++) {
                for (int iy = 1; iy <= 5; iy++) {
                    pixel.clear();
                    GL11.glReadPixels(fb.textureWidth * ix / 6, fb.textureHeight * iy / 6, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                    double lum = 0.2126 * pixel.get(0) + 0.7152 * pixel.get(1) + 0.0722 * pixel.get(2);
                    r += pixel.get(0);
                    g += pixel.get(1);
                    b += pixel.get(2);
                    a += pixel.get(3);
                    minLum = Math.min(minLum, lum);
                    maxLum = Math.max(maxLum, lum);
                    n++;
                }
            }

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            log("frame " + frame + " PIXELS avgRGBA=" + fmt(r / n) + "," + fmt(g / n) + "," + fmt(b / n) + "," + fmt(a / n)
                + " lum min/max=" + fmt(minLum) + "/" + fmt(maxLum));
            glError("samplePixels");
        }
        catch (Throwable t) {
            log("PIXELS failed " + t);
        }
    }

    private static int[] glInts(int pname, int count) {
        int[] out = new int[count];
        GL11.glGetIntegerv(pname, out);
        return out;
    }

    private static String fmt(double value) {
        return String.format("%.3f", value);
    }

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Object staticCall(String className, String method) {
        try {
            Method m = Class.forName(className).getMethod(method);
            m.setAccessible(true);
            return m.invoke(null);
        }
        catch (Throwable t) {
            return "n/a(" + t.getClass().getSimpleName() + ")";
        }
    }

    private static Object staticField(String className, String field) {
        try {
            return Class.forName(className).getField(field).get(null);
        }
        catch (Throwable t) {
            return "n/a(" + t.getClass().getSimpleName() + ")";
        }
    }

    private static Object staticFieldCall(String className, String field, String method) {
        try {
            return call(Class.forName(className).getField(field).get(null), method);
        }
        catch (Throwable t) {
            return "n/a(" + t.getClass().getSimpleName() + ")";
        }
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
