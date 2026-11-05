package dev.xardas.nekorocollectoredition.bag;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.xardas.nekorocollectoredition.CeConfig;

/** A bag with 54 slots, opened with a right click. Everything about it is set in bags.json. */
public final class BagItem extends Item {
    public static final int COUNT = 120;

    /** The bag the player just opened on this client, so the client's window knows what goes in. */
    static volatile BagItem clientOpening;

    private final int number;

    public BagItem(int number, Properties properties) {
        super(properties);
        this.number = number;
    }

    /** "bag001" to "bag120". */
    public static String itemName(int number) {
        return String.format("bag%03d", number);
    }

    private CeConfig.Bag settings() {
        return CeConfig.get().bag(number);
    }

    /** The name of its storage when it works as an ender chest (one per player and bag). */
    public String kind() {
        return itemName(number);
    }

    public boolean enderStorage() {
        return settings().enderStorage();
    }

    /** Whether this item may be put into the bag at all. Bags never go into bags. */
    public boolean accepts(ItemStack stack) {
        if (stack.getItem() instanceof BagItem) return false;
        CeConfig.Bag bag = settings();
        return bag.items().isEmpty() || bag.items().matches(stack.getItem());
    }

    /** How many slots it has. */
    public int slots() {
        return BagNames.slots(number);
    }

    /** The pickup mode until the player changes it. */
    public PickupMode defaultPickup() {
        return settings().pickup();
    }

    /** The name from bags.json (on a server: the server's, see BagNames). */
    @Override
    public Component getName(ItemStack stack) {
        String name = BagNames.name(number);
        return name == null ? super.getName(stack) : Component.literal(name);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            clientOpening = this;
        } else if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : BagMenu.OFFHAND;
            BagService.open(serverPlayer, stack, slot);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
