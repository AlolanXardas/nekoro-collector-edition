package dev.xardas.nekorocollectoredition.bag;

import java.util.List;

/**
 * The 120 bags follow the coins of Nekoro Coins: bag001-040 are the colours, bag041-080 the same colours with a
 * picture and bag081-120 with a cat. This is the text of bags.json before anyone changes it.
 */
public final class BagDefaults {
    private BagDefaults() { }

    /** Named after the Nekoro Gear materials of the same colours (since release 2). */
    private static final List<String> COLORS = List.of("Ruby", "Redstone", "Garnet", "Bronze", "Jasper", "Copper", "Carnelian", "Tumbaga",
            "Citrine", "Gold", "Topaz", "Peridot", "Jade", "Epidote", "Aventurine", "Emerald", "Chrysoprase", "Amazonite", "Mithril",
            "Chrysocolla", "Diamond", "Fluorite", "Celestine", "Sapphire", "Cobalt", "Lapis", "Lepidolite", "Spinel", "Tanzanite",
            "Sugilite", "Amethyst", "Rhodochrosite", "Kunzite", "Aquamarine", "Silver", "Iron", "Tin", "Lead", "Onyx", "Netherite");

    /**
     * What a bag of this colour is made with: the Nekoro Gear crystal or ingot of that colour, or the vanilla material.
     * Bags with a picture or a cat are made from the bag of their colour instead. Without Nekoro Gear only the vanilla ones work.
     */
    private static final List<String> RECIPE_ITEMS = List.of(
            "nekorogear:ruby", "minecraft:redstone", "nekorogear:garnet", "nekorogear:bronze_ingot", "nekorogear:jasper",
            "minecraft:copper_ingot", "nekorogear:carnelian", "nekorogear:tumbaga_ingot", "nekorogear:citrine", "minecraft:gold_ingot",
            "nekorogear:topaz", "nekorogear:peridot", "nekorogear:jade", "nekorogear:epidote", "nekorogear:aventurine",
            "minecraft:emerald", "nekorogear:chrysoprase", "nekorogear:amazonite", "nekorogear:mithril_ingot", "nekorogear:chrysocolla",
            "minecraft:diamond", "nekorogear:fluorite", "nekorogear:celestine", "nekorogear:sapphire", "nekorogear:cobalt_ingot",
            "minecraft:lapis_lazuli", "nekorogear:lepidolite", "nekorogear:spinel", "nekorogear:tanzanite", "nekorogear:sugilite",
            "minecraft:amethyst_shard", "nekorogear:rhodochrosite", "nekorogear:kunzite", "nekorogear:aquamarine", "nekorogear:silver_ingot",
            "minecraft:iron_ingot", "nekorogear:tin_ingot", "nekorogear:lead_ingot", "nekorogear:onyx", "minecraft:netherite_ingot");

    static String name(int number) {
        int color = (number - 1) % 40;
        return switch ((number - 1) / 40) {
            case 0 -> COLORS.get(color) + " Bag";
            case 1 -> COLORS.get(color) + " Emblem Bag";
            default -> COLORS.get(color) + " Cat Bag";
        };
    }

    public static String text() {
        StringBuilder out = new StringBuilder("""
                {
                  // The bags of Nekoro Collector Edition, opened with a right click. Their ids follow the coins of
                  // Nekoro Coins: bag001-040 are the colours, bag041-080 the same colours with a picture, bag081-120 with a cat.
                  // A bag:
                  //   "name"           what the game calls it
                  //   "enabled"        false: not in the creative inventory and no recipe (a bag someone already has still works)
                  //   "ender_storage"  true: one shared storage per player and bag, like an ender chest (all bags with this id show
                  //                    the same items); false: every bag holds its own items
                  //   "slots"          how many slots, 1 to 256; a bag with more than 54 scrolls. With fewer than before, the items
                  //                    in the slots that are gone are not lost: they come back when the bag has those slots again
                  //   "items"          what may go in; empty = everything. "minecraft:oak_planks" (one item), "#c:foods" (a tag),
                  //                    "somemod:*" (a mod), "minecraft:*_planks" (* matches any text)
                  //   "pickup"         picking up items until the player changes it: "all" (everything that may go in),
                  //                    "matching" (only what the bag already holds) or "off"
                  //   "recipe_item"    made with an ender chest in the middle, leather at the sides and below, and this item
                  //                    above it; empty = no recipe. By default the Nekoro Gear crystal or ingot of the bag's
                  //                    colour; a recipe with an item of a mod that is not installed is left out
                  //   "recipe_count"   how many of that item (1, 2 or 3)
                  //   "recipe_from"    instead of the above: made from this other bag ("bag001") together with
                  //   "recipe_with"    this item. By default a bag with a picture is the bag of its colour with an ink sac,
                  //                    a bag with a cat the bag of its colour with string

                  // false: no bag recipes at all, even the ones written below
                  "recipes_enabled": true,

                  // items picked up go into bags in the inventory
                  "auto_pickup": true,

                  "bags": {
                """);
        for (int i = 1; i <= BagItem.COUNT; i++) {
            String recipe = i <= 40
                    ? "\"recipe_item\": \"" + RECIPE_ITEMS.get(i - 1) + "\", \"recipe_count\": 1"
                    : "\"recipe_from\": \"" + BagItem.itemName((i - 1) % 40 + 1) + "\", \"recipe_with\": \""
                            + (i <= 80 ? "minecraft:ink_sac" : "minecraft:string") + "\"";
            out.append("    \"").append(BagItem.itemName(i)).append("\": { \"name\": \"").append(name(i))
                    .append("\", \"enabled\": true, \"ender_storage\": true, \"slots\": ").append(i % 40 == 0 ? BagMenu.MAX_SLOTS : BagMenu.SIZE)
                    .append(", \"items\": [], \"pickup\": \"matching\", ").append(recipe).append(" }")
                    .append(i < BagItem.COUNT ? ",\n" : "\n");
        }
        return out.append("  }\n}\n").toString();
    }
}
