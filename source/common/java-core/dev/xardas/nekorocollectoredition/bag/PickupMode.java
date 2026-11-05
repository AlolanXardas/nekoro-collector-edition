package dev.xardas.nekorocollectoredition.bag;

import java.util.Locale;

/** What a bag picks up on its own: everything it takes, only what it already holds, or nothing. */
public enum PickupMode {
    ALL, MATCHING, OFF;

    public PickupMode next(boolean back) {
        PickupMode[] all = values();
        return all[Math.floorMod(ordinal() + (back ? -1 : 1), all.length)];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PickupMode byId(String id, PickupMode fallback) {
        for (PickupMode mode : values()) if (mode.id().equals(id)) return mode;
        return fallback;
    }

    public static PickupMode byOrdinal(int ordinal) {
        PickupMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OFF;
    }
}
