package dev.xardas.nekorocollectoredition.bag;

import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import dev.xardas.nekorocollectoredition.CeConfig;

/** Opening bags and putting picked up items into them. */
public final class BagService {
    private BagService() { }

    /** Kept on bags that hold their own items, when the player changed the pickup mode. */
    private static final String PICKUP = "nekoro_pickup";

    /** One bag's items and its pickup mode, wherever they are kept. */
    private record Opened(Container items, PickupMode pickup, Consumer<PickupMode> setPickup) { }

    private static Opened of(ServerPlayer player, ItemStack stack, BagItem bag) {
        if (bag.enderStorage()) {
            BagData.Storage storage = BagData.get(player.server).of(player.getUUID(), bag.kind());
            return new Opened(storage.items, storage.pickup(bag.defaultPickup()), storage::setPickup);
        }
        SimpleContainer items = new SimpleContainer(BagMenu.MAX_SLOTS);
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        contents.copyInto(items.getItems());
        // every change is written back to the bag itself
        items.addListener(container -> stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items.getItems())));
        String saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(PICKUP);
        return new Opened(items, PickupMode.byId(saved, bag.defaultPickup()),
                mode -> CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PICKUP, mode.id())));
    }

    /** @param slot inventory index of the bag (hotbar slot or the offhand) */
    public static void open(ServerPlayer player, ItemStack stack, int slot) {
        if (!(stack.getItem() instanceof BagItem bag)) return;
        Opened opened = of(player, stack, bag);
        boolean ender = bag.enderStorage();
        PickupMode[] pickup = { opened.pickup() };
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BagMenu(id, inventory, opened.items(), bag.slots(),
                ender ? -1 : slot, ender ? ItemStack.EMPTY : stack, bag::accepts,
                () -> pickup[0],
                mode -> {
                    pickup[0] = mode;
                    opened.setPickup().accept(mode);
                }), stack.getHoverName()));
    }

    /**
     * Before an item on the ground is picked up: as much as fits goes into bags in the inventory, first bag first.
     * A bag takes it in mode All when the item may go into it, in mode Matching only when it already holds some.
     * Returns true when nothing is left on the ground.
     */
    public static boolean onPickup(ServerPlayer player, ItemEntity entity) {
        if (!CeConfig.get().autoPickup) return false;
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || stack.getItem() instanceof BagItem) return false;
        int before = stack.getCount();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size() && !stack.isEmpty(); i++) {
            fill(player, inventory.items.get(i), stack);
        }
        if (!stack.isEmpty()) fill(player, inventory.offhand.get(0), stack);
        int taken = before - stack.getCount();
        if (taken == 0) return false;
        if (stack.isEmpty()) {
            player.take(entity, taken);
            entity.discard();
            return true;
        }
        entity.setItem(stack.copy());
        return false;
    }

    private static void fill(ServerPlayer player, ItemStack bagStack, ItemStack stack) {
        if (!(bagStack.getItem() instanceof BagItem bag) || !bag.accepts(stack)) return;
        // a bag that holds its own items and is open right now: its window would write over the change
        if (player.containerMenu instanceof BagMenu menu && menu.bagStack() == bagStack) return;
        Opened opened = of(player, bagStack, bag);
        if (opened.pickup() == PickupMode.OFF) return;
        Container items = opened.items();
        if (opened.pickup() == PickupMode.MATCHING) {
            boolean holds = false;
            for (int i = 0; i < items.getContainerSize() && !holds; i++) {
                holds = ItemStack.isSameItemSameComponents(items.getItem(i), stack);
            }
            if (!holds) return;
        }
        int limit = items.getMaxStackSize(stack);
        int size = Math.min(items.getContainerSize(), bag.slots());
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            ItemStack in = items.getItem(i);
            if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < limit) {
                int moved = Math.min(limit - in.getCount(), stack.getCount());
                in.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (items.getItem(i).isEmpty()) items.setItem(i, stack.split(Math.min(limit, stack.getCount())));
        }
        items.setChanged();
    }
}
