package dev.xardas.nekorocollectoredition;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The window of the Collector Table: one slot that adds the block in it to the collection (the block stays there
 * until it is taken back or converted) and the player's inventory. The collection itself is drawn by the screen.
 */
public final class CollectionMenu extends AbstractContainerMenu {
    public static final int BUTTON_CONVERT = 0;
    public static final int INPUT_X = 21, INPUT_Y = 36;
    public static final int INVENTORY_X = 60, INVENTORY_Y = 156, HOTBAR_Y = 214;
    private static final int PLAYER_SLOTS_START = 1, PLAYER_SLOTS_END = 37;

    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer input = new SimpleContainer(1) {
        @Override
        public int getMaxStackSize() {
            // as much as the item allows (mods with bigger stacks included)
            return Integer.MAX_VALUE / 2;
        }
    };

    /** On the client. */
    public CollectionMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public CollectionMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(NekoroCollectorEdition.MENU.get(), id);
        this.access = access;
        this.player = inventory.player;
        input.addListener(this::slotsChanged);
        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                ItemRules rules = rules();
                return rules != null && rules.canAdd(stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    private ItemRules rules() {
        return player.level().isClientSide ? ItemRules.client : ItemRules.server;
    }

    public SimpleContainer input() {
        return input;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (player instanceof ServerPlayer serverPlayer) CollectionService.record(serverPlayer, input.getItem(0));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CONVERT && player instanceof ServerPlayer serverPlayer) {
            CollectionService.convert(serverPlayer, this);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, PLAYER_SLOTS_START, PLAYER_SLOTS_END, true)) return ItemStack.EMPTY;
        } else if (slots.get(0).hasItem() && slots.get(0).mayPlace(stack)) {
            // the slot is taken: shift+click converts the stack straight into points
            if (player instanceof ServerPlayer serverPlayer && !CollectionService.convert(serverPlayer, stack)) return ItemStack.EMPTY;
            slot.setByPlayer(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        } else if (slots.get(0).mayPlace(stack) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < PLAYER_SLOTS_END - 9) {
            if (!moveItemStackTo(stack, PLAYER_SLOTS_END - 9, PLAYER_SLOTS_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_SLOTS_START, PLAYER_SLOTS_END - 9, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, input);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NekoroCollectorEdition.BLOCK.get());
    }
}
