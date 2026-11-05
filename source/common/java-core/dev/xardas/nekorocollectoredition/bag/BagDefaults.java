package dev.xardas.nekorocollectoredition.bag;

import java.util.List;

/**
 * The 120 bags follow the coins of Nekoro Coins: bag001-040 are the colours, bag041-080 the same colours with a
 * picture and bag081-120 with a cat. This is the text of bags.json before anyone changes it.
 */
public final class BagDefaults {
    private BagDefaults() { }

    private static final List<String> COLORS = List.of("Red", "Redstone", "Mahogany", "Chocolate", "Brown", "Copper", "Orange", "Brass",
            "Yellow", "Gold", "Bone", "Lime", "Green", "Olive", "Endereye", "Emerald", "Slime", "Aqua", "Enderpearl", "Sculk", "Diamond",
            "Prismarine", "Cyan", "Blue", "Navy", "Lapis", "Chorus", "Azalea", "Purple", "Magenta", "Amethyst", "Pink", "Rose", "Snow",
            "White", "Iron", "Ash", "Gray", "Obsidian", "Netherite");

    private static final List<String> PICTURES = List.of("Fire", "Exclamation", "Pickaxe", "Ground", "Spiral", "Scales", "Fighting", "Eye",
            "Electric", "Currency", "Normal", "Grass", "Bug", "Toxic", "Hashtag", "Creeper", "Recycling", "Square", "Rectangle", "Cursor",
            "Cross", "Calendar", "Flying", "Water", "Dragon", "Tornado", "Clock", "Poison", "Harmony", "Fairy", "Ball", "Psychic",
            "Question", "Ice", "Stellar", "Steel", "Rock", "Dark", "Ghost", "Error");

    /** What a bag of this colour is made with; a bag with a picture takes two of it, a bag with a cat three. */
    private static final List<String> RECIPE_ITEMS = List.of("red_dye", "redstone", "mangrove_log", "cocoa_beans", "brown_dye", "copper_ingot",
            "orange_dye", "raw_gold", "yellow_dye", "gold_ingot", "bone", "lime_dye", "green_dye", "moss_block", "ender_eye", "emerald",
            "slime_ball", "prismarine_crystals", "ender_pearl", "sculk", "diamond", "prismarine_shard", "cyan_dye", "blue_dye", "ink_sac",
            "lapis_lazuli", "chorus_fruit", "azalea", "purple_dye", "magenta_dye", "amethyst_shard", "pink_dye", "rose_bush", "snowball",
            "white_dye", "iron_ingot", "light_gray_dye", "gray_dye", "obsidian", "netherite_ingot");

    static String name(int number) {
        int color = (number - 1) % 40;
        return switch ((number - 1) / 40) {
            case 0 -> COLORS.get(color) + " Bag";
            case 1 -> PICTURES.get(color) + " Bag";
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
                  //                    above it; empty = no recipe
                  //   "recipe_count"   how many of that item (1, 2 or 3)

                  // items picked up go into bags in the inventory
                  "auto_pickup": true,

                  "bags": {
                """);
        for (int i = 1; i <= BagItem.COUNT; i++) {
            String item = "minecraft:" + RECIPE_ITEMS.get((i - 1) % 40);
            out.append("    \"").append(BagItem.itemName(i)).append("\": { \"name\": \"").append(name(i))
                    .append("\", \"enabled\": true, \"ender_storage\": true, \"slots\": ").append(i % 40 == 0 ? BagMenu.MAX_SLOTS : BagMenu.SIZE)
                    .append(", \"items\": [], \"pickup\": \"matching\", \"recipe_item\": \"")
                    .append(item).append("\", \"recipe_count\": ").append((i - 1) / 40 + 1).append(" }")
                    .append(i < BagItem.COUNT ? ",\n" : "\n");
        }
        return out.append("  }\n}\n").toString();
    }
}
