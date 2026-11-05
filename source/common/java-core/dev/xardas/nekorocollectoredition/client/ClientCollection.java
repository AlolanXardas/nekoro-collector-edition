package dev.xardas.nekorocollectoredition.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.world.item.Item;

import dev.xardas.nekorocollectoredition.ItemRules;
import dev.xardas.nekorocollectoredition.Payloads;
import dev.xardas.nekorocollectoredition.bag.BagNames;

/** What the client knows about its own collection, from the server's packets. */
public final class ClientCollection {
    private ClientCollection() { }

    /** In the order they were added (the server keeps that order). */
    private static final List<Item> ITEMS = new ArrayList<>();
    private static final Set<Item> KNOWN = new HashSet<>();
    private static final Set<Item> FAVORITES = new HashSet<>();
    private static final Set<Item> HIDDEN = new HashSet<>();
    private static boolean showAll;
    private static long blocks;
    /** Changes whenever the list changes, so the screen knows when to filter again. */
    private static int version;
    /** Index into the take amounts; kept while the game runs. */
    private static int amountIndex;

    public static void onRules(Payloads.Rules payload) {
        ItemRules.client = ItemRules.of(payload.kinds(), payload.kindsMods(), payload.furniture(), payload.blocksPerCopy(), payload.takeAmounts(),
                payload.allowed(), payload.blocked());
        showAll = payload.showAll();
        version++;
    }

    public static void onBags(Payloads.Bags payload) {
        BagNames.set(payload.names(), payload.enabled(), payload.slots());
    }

    public static void onSync(Payloads.Sync payload) {
        if (payload.reset()) {
            ITEMS.clear();
            KNOWN.clear();
            FAVORITES.clear();
            HIDDEN.clear();
        }
        FAVORITES.addAll(payload.favorites());
        HIDDEN.addAll(payload.hidden());
        for (Item item : payload.items()) {
            if (KNOWN.add(item)) ITEMS.add(item);
        }
        blocks = payload.blocks();
        version++;
    }

    public static List<Item> items() {
        return ITEMS;
    }

    /** The server shows everything that can be added, not only the collection (show_all_items). */
    public static boolean showAll() {
        return showAll;
    }

    public static boolean has(Item item) {
        return KNOWN.contains(item);
    }

    public static boolean isFavorite(Item item) {
        return FAVORITES.contains(item);
    }

    public static boolean isHidden(Item item) {
        return HIDDEN.contains(item);
    }

    /** Switches the favourite (or hidden) mark here and on the server. */
    public static void toggle(Item item, boolean favorite) {
        Set<Item> set = favorite ? FAVORITES : HIDDEN;
        boolean value = !set.contains(item);
        if (value) {
            set.add(item);
            // a block is either a favourite or hidden, never both
            (favorite ? HIDDEN : FAVORITES).remove(item);
        } else {
            set.remove(item);
        }
        ClientPlatform.INSTANCE.sendToServer(new Payloads.Mark(item, favorite, value));
        version++;
    }

    public static int version() {
        return version;
    }

    public static long blocks() {
        return blocks;
    }

    public static int blocksPerCopy() {
        ItemRules rules = ItemRules.client;
        return rules == null ? 10 : rules.blocksPerCopy;
    }

    public static int amount() {
        List<Integer> amounts = amounts();
        return amounts.get(Math.floorMod(amountIndex, amounts.size()));
    }

    public static void nextAmount(boolean back) {
        amountIndex = Math.floorMod(amountIndex + (back ? -1 : 1), amounts().size());
    }

    private static List<Integer> amounts() {
        ItemRules rules = ItemRules.client;
        return rules == null ? List.of(1) : rules.takeAmounts;
    }

    /** The take amount on its button: up to 9999 in full, then shortened. */
    public static String amountLabel(int n) {
        return n < 10_000 ? Integer.toString(n) : shortNumber(n);
    }

    /** 1000 -> 1k, 1500 -> 1.5k, 25000 -> 25k, 2000000 -> 2M. */
    public static String shortNumber(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return withUnit(n, 1000, "k");
        if (n < 1_000_000_000) return withUnit(n, 1_000_000, "M");
        return withUnit(n, 1_000_000_000, "G");
    }

    private static String withUnit(long n, long unit, String suffix) {
        long tenths = n * 10 / unit;
        if (tenths >= 100 || tenths % 10 == 0) return (tenths / 10) + suffix;
        return (tenths / 10) + "." + (tenths % 10) + suffix;
    }

    /** How many copies the points pay for, with one decimal (12.3), or shortened when large. */
    public static String copies() {
        long tenths = blocks * 10 / blocksPerCopy();
        if (tenths < 10_000) return (tenths / 10) + "." + (tenths % 10);
        return shortNumber(tenths / 10);
    }
}
