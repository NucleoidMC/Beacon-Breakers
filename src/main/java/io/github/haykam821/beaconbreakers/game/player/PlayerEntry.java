package io.github.haykam821.beaconbreakers.game.player;

import java.util.Optional;
import java.util.UUID;

import io.github.haykam821.beaconbreakers.mixin.LivingEntityAccessor;
import io.github.haykam821.beaconbreakers.mixin.PlayerAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import io.github.haykam821.beaconbreakers.Main;
import io.github.haykam821.beaconbreakers.game.player.team.TeamEntry;
import xyz.nucleoid.plasmid.api.util.InventoryUtil;

public class PlayerEntry {
	private ServerPlayer player;
	private UUID uuid;
	private Component name;

	private final TeamEntry team;

	public PlayerEntry(ServerPlayer player, TeamEntry team) {
		this.player = player;
		this.team = team;
	}

	/**
	 * {@return whether the player should be eliminated}
	 */
	public boolean tick() {
		if (this.player == null) {
			return this.team.getPhase().getInvulnerability() == 0;
		}

		return !this.team.getPhase().getMap().getBox().isInside(player.blockPosition());
	}

	public void removePlayer() {
		this.uuid = this.player.getUUID();
		this.name = this.player.getDisplayName();

		this.player = null;
	}

	public void restorePlayer(ServerPlayer player) {
		this.uuid = null;
		this.name = null;

		this.player = player;
	}

	public void giveRespawnBeacon() {
		if (this.player == null) return;
		if (!(this.team.getBeacon() instanceof BeaconPlacement.Unplaced)) return;

		HolderLookup.Provider registries = this.player.level().registryAccess();
		HolderLookup<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
		Optional<HolderSet.Named<Block>> maybeBeacons = blocks.get(Main.RESPAWN_BEACONS);
		if (maybeBeacons.isPresent()) {
			Optional<Holder<Block>> maybeBeacon = maybeBeacons.get().getRandomElement(this.player.getRandom());
			if (maybeBeacon.isPresent()) {
				this.player.addItem(new ItemStack(maybeBeacon.get().value()));
			}
		}
	}

	public void initializePlayer() {
		if (this.player == null) return;

		this.player.setGameMode(GameType.SURVIVAL);

		InventoryUtil.clear(player);

		if (this.player == this.team.getMainPlayer().getPlayer()) {
			this.giveRespawnBeacon();
		}

		int invulnerability = this.team.getPhase().getInvulnerability();

		this.player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, invulnerability, 1, true, false));
		this.player.addEffect(new MobEffectInstance(MobEffects.SATURATION, invulnerability, 127, true, false));
	}

	public void dropInventory(DamageSource source) {
		((PlayerAccessor) this.player).callDestroyVanishingCursedItems();
		this.player.getInventory().dropAll();
		((LivingEntityAccessor) this.player).callDropExperience(this.player.level(), source.getEntity());
		((PlayerAccessor) this.player).callRemoveEntitiesOnShoulder();
	}

	public void sendMessage(Component message, boolean overlay) {
		if (this.player != null) {
			this.player.sendSystemMessage(message, overlay);
		}
	}

	public Component getName() {
		return this.player == null ? this.name : this.player.getDisplayName();
	}

	public ServerPlayer getPlayer() {
		return this.player;
	}

	public UUID getUuid() {
		return this.uuid;
	}

	public TeamEntry getTeam() {
		return this.team;
	}

	@Override
	public String toString() {
		return "PlayerEntry{player=" + this.getPlayer() + ", team=" + this.team + "}";
	}
}