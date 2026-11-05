package dev.xardas.nekorocollectoredition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;

/**
 * What can be added to the collection. The server builds it from its config and sends the same rules to players,
 * so the window can dim items that cannot go in.
 *
 * <p>An item can be added when it is not blocked and it is either allowed by name, or a block of one of the enabled
 * kinds (found by what the block is in the game, so it works with any mod), or a block of a furniture mod. Food,
 * drinks and items that only plant or lay something (seeds, string) are never blocks.
 */
public final class ItemRules {
    /** From the config of this game or server. */
    public static volatile ItemRules server;
    /** Received from the server when the block was opened (only used in the window). */
    public static volatile ItemRules client;

    /** The kinds of blocks, as named in allowed.json. */
    public static final List<String> KINDS = List.of("full_blocks", "slabs", "stairs", "walls", "fences", "doors", "trapdoors",
            "panes", "carpets", "buttons", "pressure_plates");

    public final Set<String> kinds;
    public final List<String> kindsModsEntries;
    public final List<String> furnitureEntries;
    public final int blocksPerCopy;
    public final List<Integer> takeAmounts;
    public final List<String> allowedEntries;
    public final List<String> blockedEntries;
    private final ItemFilter kindsMods;
    private final ItemFilter furniture;
    private final ItemFilter allowed;
    private final ItemFilter blocked;
    // tags can change on /reload, so the answers are cached per tag load (see clearCaches)
    private final Map<Item, Boolean> cache = new ConcurrentHashMap<>();

    private ItemRules(List<String> kinds, List<String> kindsMods, List<String> furniture, int blocksPerCopy, List<Integer> takeAmounts,
                      List<String> allowed, List<String> blocked) {
        this.kinds = Set.copyOf(kinds);
        this.kindsModsEntries = List.copyOf(kindsMods);
        this.kindsMods = ItemFilter.of(kindsMods);
        this.furnitureEntries = List.copyOf(furniture);
        this.blocksPerCopy = Math.max(1, blocksPerCopy);
        this.takeAmounts = List.copyOf(takeAmounts.isEmpty() ? List.of(1) : takeAmounts);
        this.allowedEntries = List.copyOf(allowed);
        this.blockedEntries = List.copyOf(blocked);
        this.furniture = ItemFilter.of(furniture);
        this.allowed = ItemFilter.of(allowed);
        this.blocked = ItemFilter.of(blocked);
    }

    public static ItemRules of(List<String> kinds, List<String> kindsMods, List<String> furniture, int blocksPerCopy,
                               List<Integer> takeAmounts, List<String> allowed, List<String> blocked) {
        return new ItemRules(kinds, kindsMods, furniture, blocksPerCopy, takeAmounts, allowed, blocked);
    }

    /** After tags are (re)loaded. */
    public static void clearCaches() {
        ItemRules s = server, c = client;
        if (s != null) s.cache.clear();
        if (c != null) c.cache.clear();
    }

    /** Whether this exact stack can be put into the block. */
    public boolean canAdd(ItemStack stack) {
        return !stack.isEmpty() && !hasContents(stack) && allows(stack.getItem());
    }

    /** Whether this kind of item may be in the collection (and taken out). */
    public boolean allows(Item item) {
        return cache.computeIfAbsent(item, this::check);
    }

    private boolean check(Item item) {
        // blocked always wins, so a cake (a block, but also food) or TNT stays out whatever else allows it
        if (blocked.matches(item)) return false;
        if (allowed.matches(item)) return true;
        // items that only plant or lay something (potatoes, seeds, string, redstone dust) are block items too, but not blocks
        if (!(item instanceof BlockItem blockItem) || item instanceof ItemNameBlockItem) return false;
        // food and drinks that can be placed (cups of tea, bottles of wine from mods) are not building blocks either
        if (consumable(item)) return false;
        if (furniture.matches(item)) return true;
        // the kinds count only for blocks of the mods in kinds_mods (all mods by default)
        if (!kindsMods.matches(item)) return false;
        String kind = kindOf(item);
        return kind != null && kinds.contains(kind);
    }

    private static final Map<Item, String> KIND_CACHE = new ConcurrentHashMap<>();

    /** Which of KINDS the item's block is, or null (not a block, or a block of no kind, e.g. furniture). */
    public static String kindOf(Item item) {
        if (!(item instanceof BlockItem blockItem) || item instanceof ItemNameBlockItem) return null;
        String kind = KIND_CACHE.computeIfAbsent(item, i -> {
            String k = kind(blockItem.getBlock(), BuiltInRegistries.ITEM.getKey(item).getPath());
            return k == null ? "" : k;
        });
        return kind.isEmpty() ? null : kind;
    }

    /** Which of KINDS the block is, or null; path is the item's name (ropes are built like bars, but are not panes). */
    private static String kind(Block block, String path) {
        if (block instanceof SlabBlock) return "slabs";
        if (block instanceof StairBlock) return "stairs";
        if (block instanceof WallBlock) return "walls";
        if (block instanceof FenceBlock || block instanceof FenceGateBlock) return "fences";
        if (block instanceof DoorBlock) return "doors";
        if (block instanceof TrapDoorBlock) return "trapdoors";
        if (block instanceof IronBarsBlock) {
            return path.contains("pane") || path.contains("bars") || path.contains("glass") || path.contains("window") ? "panes" : null;
        }
        if (block instanceof CarpetBlock) return "carpets";
        if (block instanceof ButtonBlock) return "buttons";
        if (block instanceof BasePressurePlateBlock) return "pressure_plates";
        return plainFullBlock(block) ? "full_blocks" : null;
    }

    /**
     * A whole cube without a function: stone, planks, bricks, glass, wool, leaves... Not blocks with a block entity
     * (chests, machines, altars, spawners), plants and their growth stages, crystals, fluids or TNT.
     */
    private static boolean plainFullBlock(Block block) {
        if (block instanceof EntityBlock || block instanceof BushBlock || block instanceof GrowingPlantBlock || block instanceof LiquidBlock
                || block instanceof TntBlock || block instanceof AmethystClusterBlock || block instanceof BuddingAmethystBlock) {
            return false;
        }
        // blocks used for something: work tables, redstone parts, lamps, pumpkins (carved with shears)
        if (block instanceof CraftingTableBlock || block instanceof CartographyTableBlock || block instanceof NoteBlock
                || block instanceof ObserverBlock || block instanceof TargetBlock || block instanceof RedstoneLampBlock
                || block instanceof PumpkinBlock || block instanceof CarvedPumpkinBlock || block instanceof CoralBlock
                || block instanceof ChorusFlowerBlock || block instanceof ChorusPlantBlock) {
            return false;
        }
        try {
            if (block.defaultBlockState().isSignalSource()) return false;
            // anything that grows (chorus flowers, cactus, crops of any mod) has an age
            for (var property : block.defaultBlockState().getProperties()) {
                if (property.getName().equals("age")) return false;
            }
            return block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // a block that needs a real world to know its shape
            return false;
        }
    }

    /** Eaten or drunk: has food properties, or is used with the eating or drinking animation. */
    private static boolean consumable(Item item) {
        try {
            ItemStack stack = new ItemStack(item);
            if (stack.has(DataComponents.FOOD)) return true;
            UseAnim animation = item.getUseAnimation(stack);
            return animation == UseAnim.EAT || animation == UseAnim.DRINK;
        } catch (RuntimeException e) {
            // an item that cannot answer without a world is treated as not edible
            return false;
        }
    }

    /** Shulker boxes with items, chests picked with their contents and similar: copying them would lose the items. */
    public static boolean hasContents(ItemStack stack) {
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyStream().findAny().isPresent()) return true;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) return true;
        return stack.has(DataComponents.BLOCK_ENTITY_DATA) || stack.has(DataComponents.CONTAINER_LOOT);
    }
}
