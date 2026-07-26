package io.github.haykam821.beaconbreakers.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.haykam821.beaconbreakers.game.event.AfterBlockPlaceListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.stimuli.EventInvokers;
import xyz.nucleoid.stimuli.Stimuli;

@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"))
	private void invokeAfterBlockPlaceListeners(BlockPos pos, Level level, Player player, ItemStack itemStack, BlockState placedState, CallbackInfoReturnable<Boolean> ci) {
		if (level.isClientSide()) return;

		try (EventInvokers invokers = Stimuli.select().forEntityAt(player, pos)) {
			invokers.get(AfterBlockPlaceListener.EVENT).afterBlockPlace(pos, level, (ServerPlayer) player, itemStack, placedState);
		}
	}
}