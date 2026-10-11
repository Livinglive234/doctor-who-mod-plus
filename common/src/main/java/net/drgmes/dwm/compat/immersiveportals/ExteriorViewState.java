package net.drgmes.dwm.compat.immersiveportals;

/**
 * Whether the console monitor's exterior view is open on this client. Kept apart from the screen so code that only
 * wants to know (the flyover's sound) doesn't pull in Immersive Portals' classes through it.
 */
public final class ExteriorViewState {
    public static volatile boolean open = false;

    // Set while the view's own picture is being rendered (at the end of the game frame, by Immersive Portals).
    public static volatile boolean rendering = false;

    private ExteriorViewState() {
    }
}
