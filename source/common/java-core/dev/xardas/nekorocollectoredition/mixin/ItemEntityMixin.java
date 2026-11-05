package dev.xardas.nekorocollectoredition.mixin;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import dev.xardas.nekorocollectoredition.bag.BagService;

/** Picked up items go into bags first (only when the player could pick the item up now). */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    private int pickupDelay;

    @Shadow
    @Nullable
    private UUID target;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void nekoro$fillBags(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || pickupDelay > 0) return;
        if (target != null && !target.equals(player.getUUID())) return;
        if (BagService.onPickup(serverPlayer, (ItemEntity) (Object) this)) ci.cancel();
    }
}
