package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.DWM;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;

import java.lang.reflect.Field;

/**
 * Iris sizes its render targets from, and attaches its depth buffer to, whatever the game's "main framebuffer" is at the
 * time, and only re-attaches when that framebuffer's depth (or colour) buffer version number differs from the one it last
 * saw. The version is just a count of how often the buffers were recreated, so the Exterior View's framebuffer (which
 * stands in as the main one while it is drawn) can carry the same number as the real screen's. A shader pipeline that last
 * saw one then keeps drawing into the other's depth buffer: the player's own view comes out washed out and wrong when they
 * walk out of the TARDIS into the world the view was showing, and the view itself can show the player's view in place of
 * the exterior. Keeping the exterior framebuffer's numbers in a range the real one never reaches makes Iris re-attach.
 * Reflection only, so it does nothing without Iris.
 */
@Environment(EnvType.CLIENT)
public final class IrisFramebufferVersions {
    private static final int EXTERIOR_RANGE = 1_000_000;
    private static final String[] FIELDS = {"iris$depthBufferVersion", "iris$colorBufferVersion"};

    private static Field[] fields;
    private static boolean unavailable;

    private IrisFramebufferVersions() {
    }

    /** Moves the framebuffer's version numbers out of the range the game's own framebuffers use. */
    public static void separate(Framebuffer framebuffer) {
        if (framebuffer == null || !init()) return;

        try {
            for (Field field : fields) {
                int value = field.getInt(framebuffer);
                if (value < EXTERIOR_RANGE) field.setInt(framebuffer, value + EXTERIOR_RANGE);
            }
        }
        catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean init() {
        if (unavailable) return false;
        if (fields != null) return true;

        try {
            Field[] found = new Field[FIELDS.length];
            for (int i = 0; i < FIELDS.length; i++) {
                found[i] = Framebuffer.class.getDeclaredField(FIELDS[i]);
                found[i].setAccessible(true);
            }

            fields = found;
            return true;
        }
        catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    private static void fail(Throwable t) {
        unavailable = true;
        fields = null;
        DWM.LOGGER.warn("[ExteriorView] Iris framebuffer versions unavailable: {}", t.toString());
    }
}
