package net.drgmes.dwm.compat.immersiveportals;

/**
 * Where the exterior view's camera was when its screen was last closed, so /dwmcam can say - the screen itself has to
 * be closed to type a command. Kept apart from the screen so the command doesn't pull in Immersive Portals' classes.
 */
public final class ExteriorViewCameraMemory {
    public static boolean used = false;
    public static float orbitFromDoors = 0F;
    public static float pitch = 0F;
    public static double distance = 0D;

    private ExteriorViewCameraMemory() {
    }
}
