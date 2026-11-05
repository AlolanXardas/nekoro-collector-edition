package dev.xardas.nekorocollectoredition.bag;

import java.util.Map;

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

    public static void add(Map<ResourceLocation, JsonElement> recipes) {
        CeConfig.load();
        for (int i = 1; i <= BagItem.COUNT; i++) {
            CeConfig.Bag bag = CeConfig.get().bag(i);
            if (!bag.enabled() || bag.recipeItem().isEmpty()) continue;
            ResourceLocation item = ResourceLocation.tryParse(bag.recipeItem());
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) {
                NekoroCollectorEdition.LOGGER.warn("Unknown recipe_item \"{}\" of {} in bags.json, no recipe", bag.recipeItem(), BagItem.itemName(i));
                continue;
            }
            recipes.put(NekoroCollectorEdition.id(BagItem.itemName(i)), recipe(i, item, bag.recipeCount()));
        }
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
