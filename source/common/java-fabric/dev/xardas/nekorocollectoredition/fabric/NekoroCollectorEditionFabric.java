package dev.xardas.nekorocollectoredition.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import dev.xardas.nekorocollectoredition.CeConfig;
import dev.xardas.nekorocollectoredition.bag.BagMenu;
import dev.xardas.nekorocollectoredition.bag.BagItem;
import dev.xardas.nekorocollectoredition.bag.BagNames;
import dev.xardas.nekorocollectoredition.CollectionMenu;
import dev.xardas.nekorocollectoredition.CollectionService;
import dev.xardas.nekorocollectoredition.ItemRules;
import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;
import dev.xardas.nekorocollectoredition.NekoroTab;
import dev.xardas.nekorocollectoredition.Payloads;

/** Fabric entry point: registers the block and connects Fabric API events to the shared code. */
public final class NekoroCollectorEditionFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CeConfig.load();
        ResourceLocation id = NekoroCollectorEdition.id(NekoroCollectorEdition.BLOCK_NAME);
        Block block = Registry.register(BuiltInRegistries.BLOCK, id, NekoroCollectorEdition.createBlock());
        Item item = Registry.register(BuiltInRegistries.ITEM, id, NekoroCollectorEdition.createItem(block));
        Item catalog = Registry.register(BuiltInRegistries.ITEM, NekoroCollectorEdition.id(NekoroCollectorEdition.CATALOG_NAME),
                NekoroCollectorEdition.createCatalog());
        MenuType<CollectionMenu> menu = Registry.register(BuiltInRegistries.MENU, id, NekoroCollectorEdition.createMenu());
        NekoroCollectorEdition.BLOCK = () -> block;
        NekoroCollectorEdition.ITEM = () -> item;
        NekoroCollectorEdition.MENU = () -> menu;
        NekoroCollectorEdition.CATALOG = () -> catalog;
        for (int i = 1; i <= BagItem.COUNT; i++) {
            Item bag = Registry.register(BuiltInRegistries.ITEM, NekoroCollectorEdition.id(BagItem.itemName(i)), NekoroCollectorEdition.createBag(i));
            NekoroCollectorEdition.BAGS.add(() -> bag);
        }
        MenuType<BagMenu> bagMenu = Registry.register(BuiltInRegistries.MENU, NekoroCollectorEdition.id(NekoroCollectorEdition.BAG_MENU_NAME),
                NekoroCollectorEdition.createBagMenu());
        NekoroCollectorEdition.BAG_MENU = () -> bagMenu;
        Item icon = Registry.register(BuiltInRegistries.ITEM, NekoroCollectorEdition.id(NekoroTab.ICON_NAME), new Item(new Item.Properties()));
        NekoroTab.ICON = () -> icon;
        if (!NekoroTab.registered()) {
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, NekoroTab.KEY, FabricItemGroup.builder()
                    .title(Component.translatable(NekoroTab.TITLE)).icon(NekoroTab::icon).build());
        }
        ItemGroupEvents.modifyEntriesEvent(NekoroTab.KEY).register(entries -> {
            entries.accept(item);
            entries.accept(catalog);
            for (int i = 1; i <= BagItem.COUNT; i++) {
                if (BagNames.enabled(i)) entries.accept(NekoroCollectorEdition.BAGS.get(i - 1).get());
            }
        });

        PayloadTypeRegistry.playS2C().register(Payloads.Rules.TYPE, Payloads.Rules.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.Bags.TYPE, Payloads.Bags.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.Sync.TYPE, Payloads.Sync.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.Mark.TYPE, Payloads.Mark.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.Take.TYPE, Payloads.Take.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Mark.TYPE, (payload, context) -> context.server().execute(
                () -> CollectionService.mark(context.player(), payload.item(), payload.favorite(), payload.value())));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Take.TYPE,
                (payload, context) -> context.server().execute(() -> CollectionService.take(context.player(), payload.item(), payload.amount())));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> CeConfig.load());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(new Payloads.Bags(BagNames.names(), BagNames.enabled(), BagNames.slots())));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> CeConfig.load());
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> ItemRules.clearCaches());
    }
}
