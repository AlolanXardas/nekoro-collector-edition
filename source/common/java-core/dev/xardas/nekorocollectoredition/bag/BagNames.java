package dev.xardas.nekorocollectoredition.bag;

import java.util.ArrayList;
import java.util.List;

import dev.xardas.nekorocollectoredition.CeConfig;

/**
 * The names and the enabled bags as this game knows them: from its own bags.json, and on a server from the server,
 * which sends them when a player joins (Payloads.Bags), so everyone sees the same names.
 */
public final class BagNames {
    private BagNames() { }

    private static List<String> names = List.of();
    private static List<Boolean> enabled = List.of();
    private static List<Integer> slots = List.of();

    public static void set(List<String> newNames, List<Boolean> newEnabled, List<Integer> newSlots) {
        names = List.copyOf(newNames);
        enabled = List.copyOf(newEnabled);
        slots = List.copyOf(newSlots);
    }

    /** After bags.json was read. */
    public static void fromConfig(CeConfig config) {
        List<String> n = new ArrayList<>();
        List<Boolean> e = new ArrayList<>();
        List<Integer> r = new ArrayList<>();
        for (int i = 1; i <= BagItem.COUNT; i++) {
            n.add(config.bag(i).name());
            e.add(config.bag(i).enabled());
            r.add(config.bag(i).slots());
        }
        set(n, e, r);
    }

    public static List<String> names() {
        return names;
    }

    public static List<Boolean> enabled() {
        return enabled;
    }

    public static List<Integer> slots() {
        return slots;
    }

    /** How many slots the bag has. */
    public static int slots(int number) {
        return number > slots.size() ? BagMenu.SIZE : slots.get(number - 1);
    }

    /** The bag's name, or null to use the name from the language file. */
    public static String name(int number) {
        if (number > names.size()) return null;
        String name = names.get(number - 1);
        return name.isEmpty() ? null : name;
    }

    public static boolean enabled(int number) {
        return number > enabled.size() || enabled.get(number - 1);
    }
}
