package io.github.haykam821.beaconbreakers.mixin;

import org.spongepowered.asm.mixin.gen.Invoker;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.entity.player.Player.class)
public interface PlayerAccessor {
    @Invoker
    void callRemoveEntitiesOnShoulder();

    @Invoker
    void callDestroyVanishingCursedItems();
}
