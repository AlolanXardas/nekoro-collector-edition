package dev.xardas.nekorocollectoredition.bag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;

/**
 * The bags kept as extra ender chests: one storage per player and bag kind (e.g. "red"), saved with the world
 * (data/nekorocollectoredition_bags.dat).
 */
public final class BagData extends SavedData {
    private static final String FILE = NekoroCollectorEdition.MOD_ID + "_bags";
    // the type only matters for upgrading old worlds; vanilla upgrades the items inside on its own
    private static final Factory<BagData> FACTORY = new Factory<>(BagData::new, BagData::load, DataFixTypes.LEVEL);

    /** One storage. Changes to the items mark the data for saving. */
    public final class Storage {
        public final SimpleContainer items = new SimpleContainer(BagMenu.MAX_SLOTS);
        /** Null until the player changes it: then the bag's own default counts. */
        private PickupMode pickup;

        Storage() {
            items.addListener(container -> setDirty());
        }

        public PickupMode pickup(PickupMode fallback) {
            return pickup != null ? pickup : fallback;
        }

        public void setPickup(PickupMode pickup) {
            this.pickup = pickup;
            setDirty();
        }
    }

    private final Map<UUID, Map<String, Storage>> players = new HashMap<>();

    public static BagData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Storage of(UUID player, String kind) {
        return players.computeIfAbsent(player, id -> new HashMap<>()).computeIfAbsent(kind, k -> new Storage());
    }

    private static BagData load(CompoundTag tag, HolderLookup.Provider registries) {
        BagData data = new BagData();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            if (!p.hasUUID("uuid")) continue;
            CompoundTag bags = p.getCompound("bags");
            for (String kind : bags.getAllKeys()) {
                CompoundTag b = bags.getCompound(kind);
                Storage storage = data.of(p.getUUID("uuid"), kind);
                ContainerHelper.loadAllItems(b, storage.items.getItems(), registries);
                if (b.contains("pickup_mode")) storage.pickup = PickupMode.byId(b.getString("pickup_mode"), null);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((uuid, bags) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("uuid", uuid);
            CompoundTag all = new CompoundTag();
            bags.forEach((kind, storage) -> {
                CompoundTag b = ContainerHelper.saveAllItems(new CompoundTag(), storage.items.getItems(), registries);
                if (storage.pickup != null) b.putString("pickup_mode", storage.pickup.id());
                all.put(kind, b);
            });
            p.put("bags", all);
            list.add(p);
        });
        tag.put("players", list);
        return tag;
    }
}
