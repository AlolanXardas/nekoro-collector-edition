package dev.xardas.nekorocollectoredition.bag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;

/**
 * The window of a bag and the player's inventory. It always has 54 bag slots, like a large chest: they show a part of
 * the bag starting at the scrolled row, and those past the end of the bag are switched off. A bag that keeps its own
 * items (ender_storage off) is locked in its slot while open, so it cannot be moved, dropped or put into itself.
 */
public final class BagMenu extends AbstractContainerMenu {
    /** Slots and rows in the window at once. */
    public static final int SIZE = 54, ROWS = 6;
    /** The most a bag can hold: the game saves no more in one item. */
    public static final int MAX_SLOTS = 256;
    /** Inventory index of the offhand. */
    public static final int OFFHAND = 40;
    /** The pickup button: next mode, previous mode. BUTTON_SCROLL + a row scrolls the bag to that row. */
    public static final int BUTTON_PICKUP_NEXT = 0, BUTTON_PICKUP_BACK = 1, BUTTON_SCROLL = 100;

    private final Container bag;
    private final int capacity;
    private final int lockedSlot;
    private final ItemStack bagStack;
    private final Predicate<ItemStack> accepts;
    private final Supplier<PickupMode> getPickup;
    private final Consumer<PickupMode> setPickup;
    private final DataSlot pickup = DataSlot.standalone();
    private final DataSlot scroll = DataSlot.standalone();

    /** On the client: the bag the player just opened tells its size and what may go in (the server checks it again). */
    public BagMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(SIZE), BagItem.clientOpening != null ? BagItem.clientOpening.slots() : SIZE, -1, ItemStack.EMPTY,
                clientAccepts(), () -> PickupMode.OFF, mode -> { });
    }

    private static Predicate<ItemStack> clientAccepts() {
        BagItem bag = BagItem.clientOpening;
        return bag != null ? bag::accepts : stack -> !(stack.getItem() instanceof BagItem);
    }

    /**
     * @param capacity how many slots the bag has; whatever lies past them in its storage stays saved, out of reach
     * @param lockedSlot inventory index of the bag that holds the items itself, or -1 for a bag working as an ender chest
     */
    public BagMenu(int id, Inventory inventory, Container bag, int capacity, int lockedSlot, ItemStack bagStack, Predicate<ItemStack> accepts,
                   Supplier<PickupMode> getPickup, Consumer<PickupMode> setPickup) {
        super(NekoroCollectorEdition.BAG_MENU.get(), id);
        this.bag = bag;
        this.capacity = capacity;
        this.lockedSlot = lockedSlot;
        this.bagStack = bagStack;
        this.accepts = accepts;
        this.getPickup = getPickup;
        this.setPickup = setPickup;
        bag.startOpen(inventory.player);
        // the client only mirrors the 54 slots it is sent; the server shows the part of the bag the window is scrolled to
        Container view = inventory.player.level().isClientSide ? bag : new View();
        for (int i = 0; i < SIZE; i++) {
            int place = i;
            addSlot(new Slot(view, i, 8 + i % 9 * 18, 18 + i / 9 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return used(place) && accepts.test(stack);
                }

                @Override
                public boolean isActive() {
                    return used(place);
                }
            });
        }
        int offset = (shownRows() - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addPlayerSlot(inventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset);
            }
        }
        for (int col = 0; col < 9; col++) {
            addPlayerSlot(inventory, col, 8 + col * 18, 161 + offset);
        }
        pickup.set(getPickup.get().ordinal());
        addDataSlot(pickup);
        addDataSlot(scroll);
    }

    /** Whether this place in the window shows a slot of the bag. */
    private boolean used(int place) {
        return place < shownRows() * 9 && scroll.get() * 9 + place < capacity;
    }

    /** Rows of the bag on the screen at once. */
    public int shownRows() {
        return Math.min(ROWS, (capacity + 8) / 9);
    }

    /** How many rows the window can scroll. */
    public int maxScroll() {
        return (capacity + 8) / 9 - shownRows();
    }

    public int scroll() {
        return scroll.get();
    }

    private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
        if (index != lockedSlot) {
            addSlot(new Slot(inventory, index, x, y));
            return;
        }
        addSlot(new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    /** The bag that keeps its own items and is open in this window (empty for ender bags). */
    ItemStack bagStack() {
        return bagStack;
    }

    public PickupMode pickupMode() {
        return PickupMode.byOrdinal(pickup.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_SCROLL) {
            scroll.set(Math.max(0, Math.min(maxScroll(), id - BUTTON_SCROLL)));
            return true;
        }
        if (id != BUTTON_PICKUP_NEXT && id != BUTTON_PICKUP_BACK) return false;
        PickupMode mode = getPickup.get().next(id == BUTTON_PICKUP_BACK);
        setPickup.accept(mode);
        pickup.set(mode.ordinal());
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // number keys and F swap with the hotbar or offhand: the open bag must stay where it is
        if (lockedSlot >= 0 && clickType == ClickType.SWAP && (button == lockedSlot || (button == OFFHAND && lockedSlot == OFFHAND))) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (player.level().isClientSide) {
            // the client sees only the shown part; the server fills the whole bag and sends the result
            if (!moveItemStackTo(stack, 0, SIZE, false)) return ItemStack.EMPTY;
        } else if (!insert(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }

    /** Into the whole bag, also the part that is not shown: first onto the same items, then into empty slots. */
    private boolean insert(ItemStack stack) {
        if (!accepts.test(stack)) return false;
        int before = stack.getCount();
        int limit = bag.getMaxStackSize(stack);
        for (int i = 0; i < capacity && !stack.isEmpty(); i++) {
            ItemStack in = bag.getItem(i);
            if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < limit) {
                int moved = Math.min(limit - in.getCount(), stack.getCount());
                in.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < capacity && !stack.isEmpty(); i++) {
            if (bag.getItem(i).isEmpty()) bag.setItem(i, stack.split(Math.min(limit, stack.getCount())));
        }
        bag.setChanged();
        return stack.getCount() != before;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        bag.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return lockedSlot < 0 || player.getInventory().getItem(lockedSlot) == bagStack;
    }

    /**
     * The part of the bag the window is scrolled to, as a container of 54 slots. As a supplier it gives slots for the
     * whole bag: Nekoro Quality of Life sorts and moves items with those, so a scrolling bag is handled as one storage.
     */
    private final class View implements Container, Supplier<List<Slot>> {
        @Override
        public List<Slot> get() {
            List<Slot> all = new ArrayList<>();
            for (int i = 0; i < capacity; i++) {
                all.add(new Slot(bag, i, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return accepts.test(stack);
                    }
                });
            }
            return all;
        }

        private int index(int place) {
            return scroll.get() * 9 + place;
        }

        private boolean inBag(int place) {
            return index(place) < capacity;
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            return bag.isEmpty();
        }

        @Override
        public ItemStack getItem(int place) {
            return inBag(place) ? bag.getItem(index(place)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int place, int amount) {
            return inBag(place) ? bag.removeItem(index(place), amount) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int place) {
            return inBag(place) ? bag.removeItemNoUpdate(index(place)) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int place, ItemStack stack) {
            if (inBag(place)) bag.setItem(index(place), stack);
        }

        @Override
        public int getMaxStackSize() {
            return bag.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            bag.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            // only ever called on whole containers; a window must not empty the bag
        }
    }
}
