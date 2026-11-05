package dev.xardas.nekorocollectoredition;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every player's collection and points, saved with the world (data/nekorocollectoredition.dat).
 * Items are kept by name, so a block from a removed mod stays saved and comes back when the mod does.
 */
public final class CollectionData extends SavedData {
    private static final String FILE = NekoroCollectorEdition.MOD_ID;
    // the type only matters for upgrading old worlds; the data here has nothing to upgrade
    private static final Factory<CollectionData> FACTORY = new Factory<>(CollectionData::new, CollectionData::load, DataFixTypes.LEVEL);

    /** One player's collection. Points are counted in converted blocks; a copy costs blocks_per_copy of them. */
    public static final class Entry {
        public final Set<ResourceLocation> items = new LinkedHashSet<>();
        public final Set<ResourceLocation> favorites = new LinkedHashSet<>();
        public final Set<ResourceLocation> hidden = new LinkedHashSet<>();
        public long blocks;
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static CollectionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Entry of(UUID player) {
        return players.computeIfAbsent(player, id -> new Entry());
    }

    private static CollectionData load(CompoundTag tag, HolderLookup.Provider registries) {
        CollectionData data = new CollectionData();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            if (!p.hasUUID("uuid")) continue;
            Entry entry = data.of(p.getUUID("uuid"));
            entry.blocks = Math.max(0, p.getLong("blocks"));
            readIds(p, "items", entry.items);
            readIds(p, "favorites", entry.favorites);
            readIds(p, "hidden", entry.hidden);
        }
        return data;
    }

    private static void readIds(CompoundTag tag, String key, Set<ResourceLocation> into) {
        for (Tag item : tag.getList(key, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
            if (id != null) into.add(id);
        }
    }

    private static ListTag writeIds(Set<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((uuid, entry) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("uuid", uuid);
            p.putLong("blocks", entry.blocks);
            p.put("items", writeIds(entry.items));
            p.put("favorites", writeIds(entry.favorites));
            p.put("hidden", writeIds(entry.hidden));
            list.add(p);
        });
        tag.put("players", list);
        return tag;
    }
}
