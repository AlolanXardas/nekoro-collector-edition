package dev.xardas.nekorocollectoredition;

import dev.xardas.nekorocollectoredition.bag.BagDefaults;
import dev.xardas.nekorocollectoredition.bag.BagItem;
import dev.xardas.nekorocollectoredition.bag.BagMenu;
import dev.xardas.nekorocollectoredition.bag.BagNames;
import dev.xardas.nekorocollectoredition.bag.PickupMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Settings from config/Nekoro/CollectorEdition: collection.json (points and the take button), allowed.json and
 * blocked.json (what can be added), bags.json (Collector Bags), material_bags.json (bags for one kind of items). On a server the server's files count; players get the rules when they open the block.
 */
public final class CeConfig {
    private static final String DEFAULT_COLLECTION = """
            {
              // Nekoro Collector Edition. Put a block into the Collector Table to add it to your collection (you keep it),
              // convert blocks into points and take copies of anything in your collection.
              // Every player has their own collection and points. On a server the server's file counts.
              // What can be added is set in allowed.json and blocked.json next to this file.

              // show everything that can be added in the window, also what is not collected yet (darkened, cannot be
              // taken); for looking through what is available, e.g. when setting up allowed.json and blocked.json
              "show_all_items": true,

              // how many converted blocks one copy costs (10 = every converted block is worth 0.1 of a copy)
              "blocks_per_copy": 10,

              // the amounts the button in the window switches between (left click: next, right click: back);
              // one click on a block in the collection takes this many (1000 is shown as 1k)
              "take_amounts": [1, 8, 16, 32, 64]
            }
            """;

    private static final String DEFAULT_ALLOWED = """
            {
              // What can be added to the Collector Table. Nothing goes in unless one of the three parts below lets it in,
              // and blocked.json always wins over all of them. Items with something inside (a filled shulker box) never go
              // in; food, drinks and items that only plant something (seeds, potatoes, string) are never counted as blocks.
              // Entries in the lists:
              //   "minecraft:oak_planks"    one item
              //   "#minecraft:planks"       every item in a tag
              //   "somemod:*"               every item of one mod
              //   "*:*_planks"              * matches any text (here: planks of every mod)

              // 1. Kinds of blocks, recognised by what the block is in the game (not by its name), so they work with
              //    blocks of any mod. Switch off what should not be copied.
              "kinds": {
                // whole cubes without a use: stone, planks, bricks, glass, wool, leaves... Never counted: blocks with
                // a block entity (chests, machines, altars, spawners), work tables, redstone parts, lamps, pumpkins,
                // plants and their growth stages, crystals, fluids and TNT
                "full_blocks": true,
                "slabs": true,
                "stairs": true,
                "walls": true,
                // fences and fence gates
                "fences": true,
                "doors": true,
                "trapdoors": true,
                // glass panes and iron bars (not ropes, which are built the same way)
                "panes": true,
                "carpets": true,
                "buttons": true,
                "pressure_plates": true
              },
              // The mods whose blocks the kinds above count for. "*" is every mod; for a strict list use e.g.
              // [ "minecraft:*", "biomesoplenty:*" ] and only blocks of those mods go in by kind.
              "kinds_mods": [ "*" ],

              // 2. Furniture and decoration mods: every block they add goes in, also blocks with a use (a drawer).
              //    Macaw's mods and their wood variants made by Every Compat:
              "furniture": [
                "mcw*:*",
                "everycomp:mc*"
              ],

              // 3. Anything else to let in (e.g. "#minecraft:flowers" or "minecraft:lantern").
              "allowed": []
            }
            """;

    private static final String DEFAULT_BLOCKED = """
            {
              // What can never be added to the collection, even when allowed.json allows it.
              // The same kinds of entries as in allowed.json.
              "blocked": [
                // ores and blocks of ores, gems and metals
                "#c:ores",
                "#c:storage_blocks",
                "minecraft:ancient_debris",
                // blocks that hold items or are valuable
                "minecraft:*shulker_box",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:dragon_egg",
                "minecraft:heavy_core",
                "minecraft:budding_amethyst",
                "minecraft:*_amethyst_bud",
                "minecraft:*_head",
                "minecraft:*_skull",
                // blocks you cannot get in survival
                "minecraft:bedrock",
                "minecraft:barrier",
                "minecraft:light",
                "minecraft:structure_void",
                "minecraft:structure_block",
                "minecraft:jigsaw",
                "minecraft:*command_block",
                "minecraft:spawner",
                "minecraft:trial_spawner",
                "minecraft:vault",
                "minecraft:end_portal_frame",
                "minecraft:reinforced_deepslate",
                "minecraft:petrified_oak_slab",
                "minecraft:infested_*",
                "minecraft:suspicious_*",
                "minecraft:frogspawn",
                "minecraft:farmland",
                "minecraft:dirt_path",
                // explosives
                "minecraft:tnt",
                // lamps, lanterns and pumpkins of every mod (light sources and crops, not building blocks)
                "*:*lamp*",
                "*:*lantern*",
                "*:*pumpkin*",
                // torches of every mod (also tiki torches and lamps of furniture mods), coral, gem blocks
                "*:*torch*",
                "*:*coral*",
                "*:*gem_block*",
                // the blocks of this mod and of Nekoro Shop
                "nekorocollectoredition:*",
                "nekoroshop:*"
              ]
            }
            """;

    /** One bag of bags.json. */
    public record Bag(String name, boolean enabled, boolean enderStorage, int slots, ItemFilter items, PickupMode pickup, String recipeItem, int recipeCount) { }

    private static final Bag NO_BAG = new Bag("", true, true, 27, ItemFilter.of(List.of()), PickupMode.MATCHING, "", 1);

    private static CeConfig current = new CeConfig(new JsonObject(), new JsonObject(), new JsonObject(), new JsonObject());

    /** The enabled kinds of blocks (ItemRules.KINDS) and the furniture mods, from allowed.json. */
    public final List<String> kinds;
    public final List<String> furniture;
    public final List<String> kindsMods;
    public final boolean showAllItems;
    public final int blocksPerCopy;
    public final List<Integer> takeAmounts;
    public final List<String> allowed;
    public final List<String> blocked;
    public final boolean autoPickup;
    /** By item name (e.g. "bag001"). */
    private final Map<String, Bag> bagSettings = new LinkedHashMap<>();

    private CeConfig(JsonObject collection, JsonObject allowedFile, JsonObject blockedFile, JsonObject bags) {
        JsonObject kindsObject = JsonFile.obj(allowedFile, "kinds");
        List<String> enabled = new ArrayList<>();
        for (String kind : ItemRules.KINDS) {
            if (JsonFile.bool(kindsObject, kind, true)) enabled.add(kind);
        }
        kinds = List.copyOf(enabled);
        furniture = List.copyOf(JsonFile.strings(allowedFile, "furniture"));
        kindsMods = allowedFile.has("kinds_mods") ? List.copyOf(JsonFile.strings(allowedFile, "kinds_mods")) : List.of("*");
        showAllItems = JsonFile.bool(collection, "show_all_items", true);
        blocksPerCopy = Math.max(1, JsonFile.integer(collection, "blocks_per_copy", 10));
        List<Integer> amounts = new ArrayList<>();
        for (int amount : JsonFile.integers(collection, "take_amounts")) {
            if (amount > 0) amounts.add(amount);
        }
        if (amounts.isEmpty()) amounts = List.of(1, 8, 16, 32, 64);
        takeAmounts = List.copyOf(amounts);
        allowed = List.copyOf(JsonFile.strings(allowedFile, "allowed"));
        blocked = List.copyOf(JsonFile.strings(blockedFile, "blocked"));
        autoPickup = JsonFile.bool(bags, "auto_pickup", true);
        JsonObject list = JsonFile.obj(bags, "bags");
        for (String name : list.keySet()) {
            JsonObject bag = JsonFile.obj(list, name);
            bagSettings.put(name, new Bag(JsonFile.string(bag, "name", ""), JsonFile.bool(bag, "enabled", true),
                    JsonFile.bool(bag, "ender_storage", true),
                    Math.max(1, Math.min(BagMenu.MAX_SLOTS, JsonFile.integer(bag, "slots", 27))),
                    ItemFilter.of(JsonFile.strings(bag, "items")),
                    PickupMode.byId(JsonFile.string(bag, "pickup", "matching"), PickupMode.MATCHING),
                    JsonFile.string(bag, "recipe_item", ""), JsonFile.integer(bag, "recipe_count", 1)));
        }
    }

    /** A bag missing from the file works with the defaults and has no recipe. */
    public Bag bag(int number) {
        return bagSettings.getOrDefault(BagItem.itemName(number), NO_BAG);
    }

    public static CeConfig get() {
        return current;
    }

    public static Path dir() {
        return Platform.INSTANCE.configDir().resolve("Nekoro").resolve("CollectorEdition");
    }

    public static void load() {
        Path dir = dir();
        current = new CeConfig(JsonFile.read(dir.resolve("collection.json"), DEFAULT_COLLECTION),
                JsonFile.read(dir.resolve("allowed.json"), DEFAULT_ALLOWED),
                JsonFile.read(dir.resolve("blocked.json"), DEFAULT_BLOCKED),
                JsonFile.read(dir.resolve("bags.json"), BagDefaults.text()));
        BagNames.fromConfig(current);
        ItemRules.server = ItemRules.of(current.kinds, current.kindsMods, current.furniture, current.blocksPerCopy, current.takeAmounts, current.allowed,
                current.blocked);
    }
}
