package io.github.haykam821.beaconbreakers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.gen.Invoker;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.entity.LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker
    void callDropExperience(final ServerLevel level, final @Nullable Entity killer);
}
