package dev.xardas.nekorocollectoredition;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import dev.xardas.nekorocollectoredition.bag.BagItem;
import dev.xardas.nekorocollectoredition.bag.BagNames;
import dev.xardas.nekorocollectoredition.client.ClientCollection;
import dev.xardas.nekorocollectoredition.neoforge.NeoForgeClient;

/** NeoForge entry point: registers the block and connects NeoForge events to the shared code. */
@Mod(NekoroCollectorEdition.MOD_ID)
public final class NekoroCollectorEditionNeoForge {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NekoroCollectorEdition.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NekoroCollectorEdition.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NekoroCollectorEdition.MOD_ID);

    static {
        DeferredBlock<Block> block = BLOCKS.register(NekoroCollectorEdition.BLOCK_NAME, NekoroCollectorEdition::createBlock);
        NekoroCollectorEdition.BLOCK = block;
        NekoroCollectorEdition.ITEM = ITEMS.register(NekoroCollectorEdition.BLOCK_NAME, () -> NekoroCollectorEdition.createItem(block.get()));
        NekoroCollectorEdition.CATALOG = ITEMS.register(NekoroCollectorEdition.CATALOG_NAME, NekoroCollectorEdition::createCatalog);
        NekoroTab.ICON = ITEMS.register(NekoroTab.ICON_NAME, () -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()));
        for (int i = 1; i <= BagItem.COUNT; i++) {
            int number = i;
            NekoroCollectorEdition.BAGS.add(ITEMS.register(BagItem.itemName(number), () -> NekoroCollectorEdition.createBag(number)));
        }
        NekoroCollectorEdition.BAG_MENU = MENUS.register(NekoroCollectorEdition.BAG_MENU_NAME, NekoroCollectorEdition::createBagMenu);
        NekoroCollectorEdition.MENU = MENUS.register(NekoroCollectorEdition.BLOCK_NAME, NekoroCollectorEdition::createMenu);
    }

    public NekoroCollectorEditionNeoForge(IEventBus modBus) {
        CeConfig.load();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((RegisterEvent event) -> event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            if (!NekoroTab.registered()) {
                helper.register(NekoroTab.KEY, CreativeModeTab.builder().title(Component.translatable(NekoroTab.TITLE)).icon(NekoroTab::icon).build());
            }
        }));
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == NekoroTab.KEY) {
                event.accept(NekoroCollectorEdition.ITEM.get());
                event.accept(NekoroCollectorEdition.CATALOG.get());
                for (int i = 1; i <= BagItem.COUNT; i++) {
                    if (BagNames.enabled(i)) event.accept(NekoroCollectorEdition.BAGS.get(i - 1).get());
                }
            }
        });
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToClient(Payloads.Rules.TYPE, Payloads.Rules.STREAM_CODEC,
                        (Payloads.Rules payload, IPayloadContext context) -> context.enqueueWork(() -> ClientCollection.onRules(payload)))
                .playToClient(Payloads.Bags.TYPE, Payloads.Bags.STREAM_CODEC,
                        (Payloads.Bags payload, IPayloadContext context) -> context.enqueueWork(() -> ClientCollection.onBags(payload)))
                .playToClient(Payloads.Sync.TYPE, Payloads.Sync.STREAM_CODEC,
                        (Payloads.Sync payload, IPayloadContext context) -> context.enqueueWork(() -> ClientCollection.onSync(payload)))
                .playToServer(Payloads.Mark.TYPE, Payloads.Mark.STREAM_CODEC,
                        (Payloads.Mark payload, IPayloadContext context) -> context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                CollectionService.mark(player, payload.item(), payload.favorite(), payload.value());
                            }
                        }))
                .playToServer(Payloads.Take.TYPE, Payloads.Take.STREAM_CODEC,
                        (Payloads.Take payload, IPayloadContext context) -> context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) CollectionService.take(player, payload.item(), payload.amount());
                        })));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerAboutToStartEvent event) -> CeConfig.load());
        bus.addListener((AddReloadListenerEvent event) -> CeConfig.load());
        bus.addListener((TagsUpdatedEvent event) -> ItemRules.clearCaches());
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Platform.INSTANCE.sendToPlayer(player, new Payloads.Bags(BagNames.names(), BagNames.enabled(), BagNames.slots()));
            }
        });

        if (FMLEnvironment.dist.isClient()) NeoForgeClient.init(modBus);
    }
}
