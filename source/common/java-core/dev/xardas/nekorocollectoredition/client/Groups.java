package dev.xardas.nekorocollectoredition.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.WoodType;

import dev.xardas.nekorocollectoredition.ItemRules;
import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;

/**
 * The other two ways to group the collection into categories: by shape (all slabs together, all chairs together) and by
 * material (everything of cherry wood together, everything of stone together). Both read the block kind and the
 * item's name, so they work with blocks of any mod.
 */
final class Groups {
    private Groups() { }

    /** Words that only describe how a material is worked; the material is the word after them. */
    private static final Set<String> MODIFIERS = Set.of("polished", "smooth", "cut", "chiseled", "cracked", "mossy", "stripped",
            "waxed", "exposed", "weathered", "oxidized", "infested", "glazed", "carved", "bricked", "tiled", "small", "large");
    /** Materials of two words that would otherwise be split (red_sandstone is not "red"). */
    private static final List<String> TWO_WORDS = List.of("red_sandstone", "red_nether", "end_stone", "dark_prismarine", "mud_brick",
            "soul_sand", "soul_soil", "nether_brick", "nether_wart", "warped_wart", "magma_block", "honeycomb_block");

    /** The last part of the item's id: "everycomp:mcfur/biomeswevegone/florus_wardrobe" -> "florus_wardrobe". */
    private static String name(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String title(String words) {
        StringBuilder out = new StringBuilder();
        for (String word : words.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** All slabs, all stairs... from the block kind; other blocks (furniture) by the last word of their name. */
    static List<CategoryBar.Category> byShape(Collection<Item> items) {
        Map<String, Set<Item>> kinds = new LinkedHashMap<>();
        for (String kind : ItemRules.KINDS) kinds.put(kind, new HashSet<>());
        Map<String, Set<Item>> others = new TreeMap<>();
        List<String> woods = woods(), colors = colors();
        for (Item item : items) {
            String kind = ItemRules.kindOf(item);
            if (kind == null) {
                // furniture named like a kind ("oak_loft_stairs" of Macaw's) joins that kind
                String word = singular(shapeWord(name(item), woods, colors));
                kind = KIND_WORDS.get(word);
                if (kind == null) {
                    others.computeIfAbsent(word, w -> new HashSet<>()).add(item);
                    continue;
                }
            }
            kinds.get(kind).add(item);
        }
        List<CategoryBar.Category> out = new ArrayList<>();
        kinds.forEach((kind, set) -> {
            if (!set.isEmpty()) {
                out.add(category("shape:" + kind, Component.translatable("gui." + NekoroCollectorEdition.MOD_ID + ".shape." + kind), set));
            }
        });
        others.forEach((word, set) -> out.add(category("shape:" + word, Component.literal(title(word)), set)));
        return out;
    }

    /** Names of shapes that mean one of the kinds, in the singular. */
    private static final Map<String, String> KIND_WORDS = Map.of("stair", "stairs", "slab", "slabs", "wall", "walls", "fence", "fences",
            "gate", "fences", "door", "doors", "trapdoor", "trapdoors", "pane", "panes", "carpet", "carpets", "button", "buttons");

    /** "stairs" -> "stair", "windows" -> "window"; "glass" and short words stay. */
    private static String singular(String word) {
        if (word.endsWith("ies") && word.length() > 4) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) return word.substring(0, word.length() - 1);
        return word;
    }

    /** Words saying where a part goes ("gutter_base_left"); the shape is the word before them. */
    private static final Set<String> POSITIONS = Set.of("base", "middle", "top", "bottom", "left", "right", "center", "centre", "corner",
            "end", "inner", "outer", "side", "single", "double", "arm", "connector", "part", "piece", "on", "off");

    /** Every wood type of the game and mods, the longest first, so "dark_oak" wins over "oak". */
    private static List<String> woods() {
        List<String> woods = new ArrayList<>();
        WoodType.values().forEach(type -> woods.add(type.name().substring(type.name().indexOf(':') + 1)));
        woods.sort(Comparator.comparingInt(String::length).reversed());
        return woods;
    }

    /** The dye colours, the longest first, so "light_blue" wins over "blue". */
    private static List<String> colors() {
        List<String> colors = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) colors.add(color.getName());
        colors.sort(Comparator.comparingInt(String::length).reversed());
        return colors;
    }

    /** "oak_four_window2" -> "window", "gutter_base_red" -> "gutter", "stripped_oak_kitchen_sink" -> "sink". */
    private static String shapeWord(String name, List<String> woods, List<String> colors) {
        String rest = "_" + name + "_";
        for (String wood : woods) rest = rest.replace("_" + wood + "_", "_");
        for (String color : colors) rest = rest.replace("_" + color + "_", "_");
        String[] words = rest.split("_");
        for (int i = words.length - 1; i >= 0; i--) {
            String word = words[i].replaceAll("\\d+$", "");
            if (word.length() > 1 && !POSITIONS.contains(word)) return word;
        }
        return name;
    }

    /** Everything of one wood, colour or stone together, whatever its shape. */
    static List<CategoryBar.Category> byMaterial(Collection<Item> items) {
        List<String> woods = woods();
        List<String> colors = colors();
        Map<String, Set<Item>> groups = new TreeMap<>();
        for (Item item : items) {
            groups.computeIfAbsent(material(name(item), woods, colors), m -> new HashSet<>()).add(item);
        }
        List<CategoryBar.Category> out = new ArrayList<>();
        groups.forEach((material, set) -> out.add(category("material:" + material, Component.literal(title(material)), set)));
        return out;
    }

    private static String material(String name, List<String> woods, List<String> colors) {
        String padded = "_" + name + "_";
        for (String wood : woods) {
            if (padded.contains("_" + wood + "_")) return wood;
        }
        for (String two : TWO_WORDS) {
            if (padded.contains("_" + two)) return two;
        }
        for (String color : colors) {
            if (name.startsWith(color + "_")) return color;
        }
        String[] words = name.split("_");
        int i = 0;
        while (i < words.length - 1 && MODIFIERS.contains(words[i])) i++;
        return words[i];
    }

    /** The category's icon is its full block when it has one, else its first item by name. */
    private static CategoryBar.Category category(String key, Component name, Set<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        Item icon = sorted.get(0);
        for (Item item : sorted) {
            if ("full_blocks".equals(ItemRules.kindOf(item))) {
                icon = item;
                break;
            }
        }
        return new CategoryBar.Category(key, name, new ItemStack(icon), items::contains);
    }
}
