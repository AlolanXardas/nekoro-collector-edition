package dev.xardas.nekorocollectoredition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import dev.xardas.nekorocollectoredition.bag.BagMenu;
import dev.xardas.nekorocollectoredition.bag.BagItem;

/** Shared constants and the registered objects; each loader registers them and fills in the suppliers. */
public final class NekoroCollectorEdition {
    public static final String MOD_ID = "nekorocollectoredition";
    public static final Logger LOGGER = LoggerFactory.getLogger("Nekoro Collector Edition");

    public static final String BLOCK_NAME = "collector_table";
    public static Supplier<Block> BLOCK;
    public static Supplier<Item> ITEM;
    public static Supplier<MenuType<CollectionMenu>> MENU;
    public static final String CATALOG_NAME = "collector_catalog";
    public static Supplier<Item> CATALOG;
    /** The 120 bags, in order of their numbers. */
    public static final List<Supplier<Item>> BAGS = new ArrayList<>();
    public static Supplier<MenuType<BagMenu>> BAG_MENU;
    public static final String BAG_MENU_NAME = "collector_bag";

    private NekoroCollectorEdition() { }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Block createBlock() {
        return new CollectorTableBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
    }

    public static Item createItem(Block block) {
        return new BlockItem(block, new Item.Properties());
    }

    public static Item createCatalog() {
        return new CollectorCatalogItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    public static Item createBag(int number) {
        return new BagItem(number, new Item.Properties().stacksTo(1));
    }

    public static MenuType<BagMenu> createBagMenu() {
        return new MenuType<>(BagMenu::new, FeatureFlags.VANILLA_SET);
    }

    public static MenuType<CollectionMenu> createMenu() {
        return new MenuType<>(CollectionMenu::new, FeatureFlags.VANILLA_SET);
    }
}
