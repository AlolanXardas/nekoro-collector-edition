package dev.xardas.nekorocollectoredition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The server side of the block: adding to the collection, converting into points and taking copies. */
public final class CollectionService {
    private CollectionService() { }

    /** Largest amount one click may ask for (more than a full inventory of 1000-stacks would still be fine). */
    private static final int MAX_TAKE = 1_000_000;

    private static CollectionData.Entry entry(ServerPlayer player) {
        return CollectionData.get(player.server).of(player.getUUID());
    }

    public static void onOpen(ServerPlayer player) {
        ItemRules rules = ItemRules.server;
        Platform.INSTANCE.sendToPlayer(player, Payloads.Rules.of(rules));
        CollectionData.Entry entry = entry(player);
        Platform.INSTANCE.sendToPlayer(player, new Payloads.Sync(true, shown(entry.items, rules),
                shown(entry.favorites, rules), shown(entry.hidden, rules), entry.blocks));
    }

    /** Blocks of removed mods and blocks no longer allowed stay saved, they are just not shown. */
    private static List<Item> shown(Iterable<ResourceLocation> ids, ItemRules rules) {
        List<Item> items = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent() && rules.allows(item.get())) items.add(item.get());
        }
        return items;
    }

    /** The block in the slot joins the collection; the player keeps it. */
    public static void record(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !ItemRules.server.canAdd(stack)) return;
        CollectionData data = CollectionData.get(player.server);
        CollectionData.Entry entry = data.of(player.getUUID());
        if (entry.items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            data.setDirty();
            Platform.INSTANCE.sendToPlayer(player, Payloads.Sync.update(List.of(stack.getItem()), entry.blocks));
        }
    }

    /** Everything in the slot turns into points (and joins the collection, if it was not there yet). */
    public static void convert(ServerPlayer player, CollectionMenu menu) {
        if (convert(player, menu.input().getItem(0))) menu.input().setItem(0, ItemStack.EMPTY);
    }

    /** The stack turns into points; false (and nothing happens) when it cannot be added. The caller removes the stack. */
    public static boolean convert(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !ItemRules.server.canAdd(stack)) return false;
        CollectionData data = CollectionData.get(player.server);
        CollectionData.Entry entry = data.of(player.getUUID());
        Item item = stack.getItem();
        boolean added = entry.items.add(BuiltInRegistries.ITEM.getKey(item));
        entry.blocks += stack.getCount();
        data.setDirty();
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        Platform.INSTANCE.sendToPlayer(player, Payloads.Sync.update(added ? List.of(item) : List.of(), entry.blocks));
        return true;
    }

    /** Favourite and hidden marks; only for blocks that are in the collection. */
    public static void mark(ServerPlayer player, Item item, boolean favorite, boolean value) {
        if (!(player.containerMenu instanceof CollectionMenu)) return;
        CollectionData data = CollectionData.get(player.server);
        CollectionData.Entry entry = data.of(player.getUUID());
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!entry.items.contains(id)) return;
        var set = favorite ? entry.favorites : entry.hidden;
        boolean changed = value ? set.add(id) : set.remove(id);
        // a block is either a favourite or hidden, never both
        if (value) changed |= (favorite ? entry.hidden : entry.favorites).remove(id);
        if (changed) data.setDirty();
    }

    /** As many copies as asked for, as far as the points and the free space in the inventory go. Nothing is dropped. */
    public static void take(ServerPlayer player, Item item, int amount) {
        if (!(player.containerMenu instanceof CollectionMenu menu) || !menu.stillValid(player)) return;
        ItemRules rules = ItemRules.server;
        CollectionData data = CollectionData.get(player.server);
        CollectionData.Entry entry = data.of(player.getUUID());
        if (!entry.items.contains(BuiltInRegistries.ITEM.getKey(item)) || !rules.allows(item)) return;

        long affordable = entry.blocks / rules.blocksPerCopy;
        int wanted = (int) Math.min(Math.min(Math.max(amount, 1), MAX_TAKE), affordable);
        int count = Math.min(wanted, freeSpace(player.getInventory(), new ItemStack(item)));
        if (count <= 0) return;

        int given = 0;
        while (given < count) {
            ItemStack copy = new ItemStack(item, Math.min(count - given, new ItemStack(item).getMaxStackSize()));
            int size = copy.getCount();
            player.getInventory().add(copy);
            given += size - copy.getCount();
            if (!copy.isEmpty()) break;
        }
        entry.blocks -= (long) given * rules.blocksPerCopy;
        data.setDirty();
        player.containerMenu.broadcastChanges();
        Platform.INSTANCE.sendToPlayer(player, Payloads.Sync.update(List.of(), entry.blocks));
    }

    /** How many of this item still fit into the main inventory and hotbar. */
    private static int freeSpace(Inventory inventory, ItemStack template) {
        long space = 0;
        int limit = inventory.getMaxStackSize(template);
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) space += limit;
            else if (ItemStack.isSameItemSameComponents(stack, template)) space += Math.max(0, limit - stack.getCount());
        }
        return (int) Math.min(space, Integer.MAX_VALUE);
    }
}
