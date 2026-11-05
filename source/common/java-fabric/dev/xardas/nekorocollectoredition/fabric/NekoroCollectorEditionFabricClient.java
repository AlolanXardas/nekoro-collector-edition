package dev.xardas.nekorocollectoredition.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

import dev.xardas.nekorocollectoredition.NekoroCollectorEdition;
import dev.xardas.nekorocollectoredition.Payloads;
import dev.xardas.nekorocollectoredition.client.ClientCollection;
import dev.xardas.nekorocollectoredition.client.BagScreen;
import dev.xardas.nekorocollectoredition.client.CollectionScreen;

public final class NekoroCollectorEditionFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(NekoroCollectorEdition.MENU.get(), CollectionScreen::new);
        MenuScreens.register(NekoroCollectorEdition.BAG_MENU.get(), BagScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Rules.TYPE, (payload, context) -> ClientCollection.onRules(payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Bags.TYPE, (payload, context) -> ClientCollection.onBags(payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Sync.TYPE, (payload, context) -> ClientCollection.onSync(payload));
    }
}
