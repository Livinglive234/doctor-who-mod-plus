package net.drgmes.dwm.enums;

public enum TardisConsoleUnitControlFlags {
    REQUIRED_MATERIALIZING_SYSTEM,
    REQUIRED_FLIGHT_SYSTEM,
    REQUIRED_SHIELDS_SYSTEM,
    MUST_BE_MATERIALIZED,
    MUST_BE_LANDED,
    DEPENDS_ON_OWNER,
    // Stricter than DEPENDS_ON_OWNER: that one still passes for anyone holding a matching key, this one is the
    // owner (or a system call with no player) only.
    OWNER_ONLY,
    DEPENDS_ON_SHIELDS_ON,
    DEPENDS_ON_HANDBRAKE_OFF,
    // A SLIDER control's "on" position slides its model the opposite way from the rest - for one sitting off on
    // its own rather than in a row of other sliders, where there's no shared "on" direction to stay consistent with.
    REVERSED_SLIDER,
}
