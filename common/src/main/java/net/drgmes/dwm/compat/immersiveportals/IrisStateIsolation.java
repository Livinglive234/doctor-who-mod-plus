package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.DWM;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Experiment: Iris keeps one global "current camera matrices / fog / tick" record shared by every render, and only
 * overwrites it part-way through each one. The Exterior View is drawn in a second render per frame, so at the start of
 * each render the shader pack's "previous frame" values come from the other render's camera, which smears anything
 * reprojected from the previous frame. This gives the exterior render its own copy of that record, kept between frames,
 * and puts the player's own back afterwards. All reflection, so it does nothing (and costs nothing) without Iris.
 */
@Environment(EnvType.CLIENT)
public final class IrisStateIsolation {
    private static final String[] FIELDS = {"gbufferModelView", "gbufferProjection", "fogColor", "fogDensity", "darknessLightFactor", "tickDelta", "realTickDelta", "cloudTime"};

    private static Object instance;
    private static Map<String, Field> fields;
    private static boolean unavailable;

    private static Map<String, Object> playerState;
    private static Map<String, Object> exteriorState;

    private IrisStateIsolation() {
    }

    static void enter() {
        if (!init()) return;

        try {
            playerState = read();
            if (exteriorState != null) write(exteriorState);
        }
        catch (Throwable t) {
            fail(t);
        }
    }

    static void leave() {
        if (!init() || playerState == null) return;

        try {
            exteriorState = read();
            write(playerState);
            playerState = null;
        }
        catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean init() {
        if (unavailable) return false;
        if (fields != null) return true;

        try {
            Class<?> type = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            instance = type.getField("INSTANCE").get(null);
            fields = new LinkedHashMap<>();

            for (String name : FIELDS) {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                fields.put(name, field);
            }

            return true;
        }
        catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    private static Map<String, Object> read() throws IllegalAccessException {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Field> e : fields.entrySet()) out.put(e.getKey(), copy(e.getValue().get(instance)));
        return out;
    }

    private static void write(Map<String, Object> state) throws IllegalAccessException {
        for (Map.Entry<String, Field> e : fields.entrySet()) e.getValue().set(instance, copy(state.get(e.getKey())));
    }

    // Matrices and vectors are mutated in place by Iris, so they are copied; the rest are immutable boxes.
    private static Object copy(Object value) {
        if (value instanceof org.joml.Matrix4f m) return new org.joml.Matrix4f(m);
        if (value instanceof org.joml.Matrix4fc m) return new org.joml.Matrix4f(m);
        if (value instanceof org.joml.Vector3d v) return new org.joml.Vector3d(v);
        return value;
    }

    private static void fail(Throwable t) {
        unavailable = true;
        fields = null;
        DWM.LOGGER.warn("[ExteriorView] Iris state isolation unavailable: {}", t.toString());
    }
}
