package net.drgmes.dwm.compat.immersiveportals;

/**
 * Whether the console monitor's exterior view is open on this client. Kept apart from the screen so code that only
 * wants to know (the flyover's sound) doesn't pull in Immersive Portals' classes through it.
 */
public final class ExteriorViewState {
    public static volatile boolean open = false;

    private ExteriorViewState() {
    }
}
