package dev.xardas.nekorocollectoredition.bag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import dev.xardas.nekorocollectoredition.CeConfig;
import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;

/** The recipes of the bags are written from bags.json each time recipes are loaded, as if they came from a data pack. */
public final class BagRecipes {
    private BagRecipes() { }

    /** The row above the ender chest: 1, 2 or 3 of the recipe item. */
    private static final String[] TOP = { " M ", "M M", "MMM" };

    private static final Map<String, Boolean> NAMESPACES = new ConcurrentHashMap<>();

    public static void add(Map<ResourceLocation, JsonElement> recipes) {
        CeConfig.load();
        if (!CeConfig.get().recipesEnabled) return;
        for (int i = 1; i <= BagItem.COUNT; i++) {
            CeConfig.Bag bag = CeConfig.get().bag(i);
            if (!bag.enabled()) continue;
            if (!bag.recipeFrom().isEmpty()) {
                // another bag plus one item, e.g. the bag of the same colour and an ink sac
                String from = bag.recipeFrom().contains(":") ? bag.recipeFrom() : NekoroCollectorEdition.MOD_ID + ":" + bag.recipeFrom();
                ResourceLocation base = known(from, i);
                ResourceLocation with = bag.recipeWith().isEmpty() ? null : known(bag.recipeWith(), i);
                if (base != null && (with != null || bag.recipeWith().isEmpty())) {
                    recipes.put(NekoroCollectorEdition.id(BagItem.itemName(i)), shapeless(i, base, with));
                }
                continue;
            }
            if (bag.recipeItem().isEmpty()) continue;
            ResourceLocation item = known(bag.recipeItem(), i);
            if (item != null) recipes.put(NekoroCollectorEdition.id(BagItem.itemName(i)), recipe(i, item, bag.recipeCount()));
        }
    }

    /** The item id if the game has it; null otherwise (quietly for items of mods that are not installed, e.g. Nekoro Gear). */
    private static ResourceLocation known(String text, int bag) {
        ResourceLocation item = ResourceLocation.tryParse(text);
        if (item != null && BuiltInRegistries.ITEM.containsKey(item)) return item;
        if (item == null || hasNamespace(item.getNamespace())) {
            NekoroCollectorEdition.LOGGER.warn("Unknown item \"{}\" in the recipe of {} in bags.json, no recipe", text, BagItem.itemName(bag));
        }
        return null;
    }

    private static JsonObject shapeless(int number, ResourceLocation base, ResourceLocation with) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredient(base.toString()));
        if (with != null) ingredients.add(ingredient(with.toString()));
        recipe.add("ingredients", ingredients);
        JsonObject result = new JsonObject();
        result.addProperty("id", NekoroCollectorEdition.id(BagItem.itemName(number)).toString());
        result.addProperty("count", 1);
        recipe.add("result", result);
        return recipe;
    }

    private static boolean hasNamespace(String namespace) {
        return NAMESPACES.computeIfAbsent(namespace,
                ns -> BuiltInRegistries.ITEM.keySet().stream().anyMatch(key -> key.getNamespace().equals(ns)));
    }

    private static JsonObject recipe(int number, ResourceLocation item, int count) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "misc");
        JsonArray pattern = new JsonArray();
        pattern.add(TOP[Math.max(1, Math.min(3, count)) - 1]);
        pattern.add("LEL");
        pattern.add(" L ");
        recipe.add("pattern", pattern);
        JsonObject key = new JsonObject();
        key.add("M", ingredient(item.toString()));
        key.add("L", ingredient("minecraft:leather"));
        key.add("E", ingredient("minecraft:ender_chest"));
        recipe.add("key", key);
        JsonObject result = new JsonObject();
        result.addProperty("id", NekoroCollectorEdition.id(BagItem.itemName(number)).toString());
        result.addProperty("count", 1);
        recipe.add("result", result);
        return recipe;
    }

    private static JsonObject ingredient(String item) {
        JsonObject out = new JsonObject();
        out.addProperty("item", item);
        return out;
    }
}
