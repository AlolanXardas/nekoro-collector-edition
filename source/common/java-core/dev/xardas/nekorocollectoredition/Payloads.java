package dev.xardas.nekorocollectoredition;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/** The packets of the mod. Handlers live in CollectionService (server) and ClientCollection (client). */
public final class Payloads {
    private Payloads() { }

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);

    /** Server -> client: what can be added and the take amounts, sent when the block is opened. */
    public record Rules(List<String> kinds, List<String> kindsMods, List<String> furniture, int blocksPerCopy, List<Integer> takeAmounts, List<String> allowed,
                        List<String> blocked, boolean showAll)
            implements CustomPacketPayload {
        public static final Type<Rules> TYPE = new Type<>(NekoroCollectorEdition.id("rules"));
        private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
        public static final StreamCodec<RegistryFriendlyByteBuf, Rules> STREAM_CODEC = StreamCodec.of((buf, r) -> {
            STRINGS.encode(buf, r.kinds);
            STRINGS.encode(buf, r.kindsMods);
            STRINGS.encode(buf, r.furniture);
            buf.writeVarInt(r.blocksPerCopy);
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, r.takeAmounts);
            STRINGS.encode(buf, r.allowed);
            STRINGS.encode(buf, r.blocked);
            buf.writeBoolean(r.showAll);
        }, buf -> new Rules(STRINGS.decode(buf), STRINGS.decode(buf), STRINGS.decode(buf), buf.readVarInt(),
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf), STRINGS.decode(buf), STRINGS.decode(buf), buf.readBoolean()));

        static Rules of(ItemRules rules) {
            return new Rules(List.copyOf(rules.kinds), rules.kindsModsEntries, rules.furnitureEntries, rules.blocksPerCopy, rules.takeAmounts, rules.allowedEntries,
                    rules.blockedEntries, CeConfig.get().showAllItems);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the names of the bags, which are enabled and their sizes, sent when a player joins. */
    public record Bags(List<String> names, List<Boolean> enabled, List<Integer> slots) implements CustomPacketPayload {
        public static final Type<Bags> TYPE = new Type<>(NekoroCollectorEdition.id("bags"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bags> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Bags::names,
                ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), Bags::enabled,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Bags::slots,
                Bags::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server -> client: the player's collection and points. When reset it is the whole collection with favourites and
     * hidden blocks, otherwise only new items (and empty favourite and hidden lists).
     */
    public record Sync(boolean reset, List<Item> items, List<Item> favorites, List<Item> hidden, long blocks) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(NekoroCollectorEdition.id("sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Sync::reset,
                ITEM.apply(ByteBufCodecs.list()), Sync::items,
                ITEM.apply(ByteBufCodecs.list()), Sync::favorites,
                ITEM.apply(ByteBufCodecs.list()), Sync::hidden,
                ByteBufCodecs.VAR_LONG, Sync::blocks,
                Sync::new);

        static Sync update(List<Item> added, long blocks) {
            return new Sync(false, added, List.of(), List.of(), blocks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: mark a block in the collection as a favourite (or not), or hide it (or show it again). */
    public record Mark(Item item, boolean favorite, boolean value) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(NekoroCollectorEdition.id("mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
                ITEM, Mark::item,
                ByteBufCodecs.BOOL, Mark::favorite,
                ByteBufCodecs.BOOL, Mark::value,
                Mark::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: take this many copies of an item from the collection. */
    public record Take(Item item, int amount) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(NekoroCollectorEdition.id("take"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Take> STREAM_CODEC = StreamCodec.composite(
                ITEM, Take::item,
                ByteBufCodecs.VAR_INT, Take::amount,
                Take::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
