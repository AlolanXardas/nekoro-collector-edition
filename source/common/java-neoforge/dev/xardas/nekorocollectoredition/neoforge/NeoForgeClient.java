package dev.xardas.nekorocollectoredition.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;
import dev.xardas.nekorocollectoredition.client.BagScreen;
import dev.xardas.nekorocollectoredition.client.CollectionScreen;

/** NeoForge client events, connected to the shared client code. */
public final class NeoForgeClient {
    private NeoForgeClient() { }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(NekoroCollectorEdition.MENU.get(), CollectionScreen::new);
            event.register(NekoroCollectorEdition.BAG_MENU.get(), BagScreen::new);
        });
    }
}
