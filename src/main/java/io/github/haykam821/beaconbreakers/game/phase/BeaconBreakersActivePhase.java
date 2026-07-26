package io.github.haykam821.beaconbreakers.game.phase;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.haykam821.beaconbreakers.Main;
import io.github.haykam821.beaconbreakers.game.BeaconBreakersConfig;
import io.github.haykam821.beaconbreakers.game.BeaconBreakersSidebar;
import io.github.haykam821.beaconbreakers.game.InvulnerabilityTimerBar;
import io.github.haykam821.beaconbreakers.game.event.AfterBlockPlaceListener;
import io.github.haykam821.beaconbreakers.game.map.BeaconBreakersMap;
import io.github.haykam821.beaconbreakers.game.map.BeaconBreakersMapConfig;
import io.github.haykam821.beaconbreakers.game.player.BeaconPlacement;
import io.github.haykam821.beaconbreakers.game.player.PlayerEntry;
import io.github.haykam821.beaconbreakers.game.player.team.MultipleTeamEntry;
import io.github.haykam821.beaconbreakers.game.player.team.SingleTeamEntry;
import io.github.haykam821.beaconbreakers.game.player.team.TeamEntry;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.GameComponents;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.team.TeamChat;
import xyz.nucleoid.plasmid.api.game.common.team.TeamManager;
import xyz.nucleoid.plasmid.api.game.common.team.TeamSelectionLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.JoinOfferResult;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerPos;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.block.BlockBreakEvent;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.world.ExplosionDetonatedEvent;

public class BeaconBreakersActivePhase {
	private final GameSpace gameSpace;
	private final ServerLevel level;
	private final BeaconBreakersMap map;
	private final BeaconBreakersConfig config;
	private final List<TeamEntry> teams;
	private final InvulnerabilityTimerBar bar;
	private final BeaconBreakersSidebar sidebar;
	private boolean singleplayer;
	private int ticksUntilClose = -1;
	private int invulnerability;

	public BeaconBreakersActivePhase(GameSpace gameSpace, ServerLevel level, TeamSelectionLobby teamSelection, TeamManager teamManager, GlobalWidgets widgets, BeaconBreakersMap map, BeaconBreakersConfig config) {
		this.gameSpace = gameSpace;
		this.level = level;
		this.map = map;
		this.config = config;

		this.sidebar = new BeaconBreakersSidebar(widgets, this);
		this.bar = new InvulnerabilityTimerBar(this, widgets);

		this.teams = config.getTeams()
			.map(teams -> MultipleTeamEntry.allocate(this, gameSpace.getPlayers(), teamSelection, teamManager, teams))
			.orElseGet(() -> SingleTeamEntry.ofAll(this, gameSpace.getPlayers()));

		this.invulnerability = this.config.getInvulnerability();
	}

	public static void open(GameSpace gameSpace, ServerLevel level, TeamSelectionLobby teamSelection, BeaconBreakersMap map, BeaconBreakersConfig config) {
		gameSpace.setActivity(activity -> {
			TeamManager teamManager = TeamManager.addTo(activity);
			GlobalWidgets widgets = GlobalWidgets.addTo(activity);

			if (config.getTeams().isPresent()) {
				TeamChat.addTo(activity, teamManager);
			}

			BeaconBreakersActivePhase active = new BeaconBreakersActivePhase(gameSpace, level, teamSelection, teamManager, widgets, map, config);

			activity.allow(GameRuleType.BLOCK_DROPS);
			activity.allow(GameRuleType.CRAFTING);
			activity.allow(GameRuleType.FALL_DAMAGE);
			activity.allow(GameRuleType.HUNGER);
			activity.deny(GameRuleType.PORTALS);
			activity.allow(GameRuleType.PVP);

			// Listeners
			activity.listen(AfterBlockPlaceListener.EVENT, active::afterBlockPlace);
			activity.listen(BlockUseEvent.EVENT, active::onUseBlock);
			activity.listen(BlockBreakEvent.EVENT, active::onBreakBlock);
			activity.listen(GameActivityEvents.ENABLE, active::enable);
			activity.listen(GameActivityEvents.TICK, active::tick);
			activity.listen(GamePlayerEvents.ACCEPT, active::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, active::onOfferPlayers);
			activity.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
			activity.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
			activity.listen(GamePlayerEvents.REMOVE, active::removePlayer);
			activity.listen(ExplosionDetonatedEvent.EVENT, active::onExplosionDetonated);
			activity.listen(ItemThrowEvent.EVENT, active::onThrowItem);
		});
	}

	private void enable() {
		this.singleplayer = this.teams.size() == 1;

		for (TeamEntry team : this.teams) {
			if (team.isEliminated()) {
				throw new IllegalStateException("Team should not be immediately eliminated");
			}

			for (PlayerEntry entry : team.getPlayers()) {
				ServerPlayer player = entry.getPlayer();

				if (player != null) {
					entry.initializePlayer();
					BeaconBreakersActivePhase.spawn(this.level, this.map, this.config.getMapConfig(), player);
				}
			}
		}

		this.sidebar.update();
	}
	
	private void tick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		if (this.invulnerability > 0) {
			this.bar.tick();

			this.invulnerability -= 1;
			if (this.invulnerability == 0) {
				this.gameSpace.getPlayers().playSound(SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1, 1);
				this.gameSpace.getPlayers().sendMessage(Component.translatable("text.beaconbreakers.invulnerability.ended").withStyle(ChatFormatting.GOLD));

				this.bar.remove();
			}
		}

		Iterator<TeamEntry> iterator = this.teams.iterator();
		boolean sidebarDirty = false;

		while (iterator.hasNext()) {
			TeamEntry entry = iterator.next();
			entry.tick();

			if (entry.isEliminated()) {
				iterator.remove();
				sidebarDirty = true;
			}
		}

		if (sidebarDirty) {
			this.sidebar.update();
		}
	
		if (this.teams.size() < 2) {
			if (this.teams.size() == 1 && this.singleplayer) return;

			this.gameSpace.getPlayers().sendMessage(this.getEndingMessage().withStyle(ChatFormatting.GOLD));
			this.ticksUntilClose = this.config.getTicksUntilClose().sample(this.level.getRandom());
		}
	}

	public boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}
	
	private MutableComponent getEndingMessage() {
		if (this.teams.size() == 1) {
			TeamEntry winner = this.teams.iterator().next();
			return winner.getWinMessage();
		}
		return Component.translatable("text.beaconbreakers.win.none");
	}

	private void setSpectator(ServerPlayer player) {
		player.setGameMode(GameType.SPECTATOR);
	}

	public void applyEliminationToPlayer(PlayerEntry entry) {
		ServerPlayer player = entry.getPlayer();

		if (player != null) {
			this.setSpectator(player);
		}

		if (!this.isGameEnding()) {
			this.gameSpace.getPlayers().sendMessage(Component.translatable("text.beaconbreakers.eliminate", entry.getName()).withStyle(ChatFormatting.RED));
		}
	}

	private void eliminate(PlayerEntry entry) {
		this.applyEliminationToPlayer(entry);

		TeamEntry team = entry.getTeam();
		team.removePlayer(entry);

		if (team.isEliminated() && this.teams.remove(team)) {
			this.sidebar.update();
		}
	}

	private PlayerEntry getEntryFromPlayer(ServerPlayer player) {
		for (TeamEntry team : this.teams) {
			for (PlayerEntry entry : team.getPlayers()) {
				if (player.equals(entry.getPlayer())) {
					return entry;
				}
			}
		}
		return null;
	}

	private PlayerEntry getEntryByUuid(UUID uuid) {
		for (TeamEntry team : this.teams) {
			for (PlayerEntry entry : team.getPlayers()) {
				if (uuid.equals(entry.getUuid())) {
					return entry;
				}
			}
		}
		return null;
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(player -> {
			if (acceptor.intent().canPlay()) {
				PlayerEntry entry = this.getEntryByUuid(player.id());

				if (entry != null) {
					Vec3 spawnPos = this.getRespawnPos(entry);

					if (spawnPos != null) {
						return new PlayerPos(this.level, spawnPos, 0, 0);
					}
				}
			}

			return new PlayerPos(this.level, BeaconBreakersActivePhase.getSpawnPos(level, map, this.config.getMapConfig()), 0, 0);
		}).thenRunForEach(player -> {
			if (acceptor.intent().canPlay()) {
				PlayerEntry entry = this.getEntryByUuid(player.getUUID());

				if (entry != null) {
					entry.restorePlayer(player);
					entry.initializePlayer();

					return;
				}
			}

			this.setSpectator(player);
		});
	}

	private JoinOfferResult onOfferPlayers(JoinOffer offer) {
		for (UUID player : offer.playerIds()) {
			PlayerEntry entry = this.getEntryByUuid(player);

			if (offer.intent().canPlay() && entry == null) {
				return offer.reject(GameComponents.Join.spectatorsOnly());
			} else if (offer.intent().canSpectate() && entry != null) {
				return offer.reject(GameComponents.Join.participantsOnly());
			}
		}

		return offer.accept();
	}

	private void removePlayer(ServerPlayer player) {
		PlayerEntry entry = this.getEntryFromPlayer(player);

		if (entry != null) {
			entry.removePlayer();
		}
	}

	private Vec3 getRespawnPos(PlayerEntry entry) {
		if (!(entry.getTeam().getBeacon() instanceof BeaconPlacement.Placed placed)) {
			return null;
		}

		BlockPos beaconPos = placed.pos();

		BlockState beaconState = this.level.getBlockState(beaconPos);
		if (!beaconState.is(Main.RESPAWN_BEACONS)) {
			return null;
		}

		int topY = this.level.getMaxY() - 2;

		Optional<Vec3> spawnOptional = RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, level, beaconPos);
		if (spawnOptional.isPresent()) {
			Vec3 spawn = spawnOptional.get();
			if (spawn.y() <= topY) {
				return spawn;
			}
		}
	
		Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(this.level.getRandom());
		float widthOffset = beaconPos.getY() > topY ? EntityTypes.PLAYER.getWidth() : 0;

		double x = beaconPos.getX() + 0.5 + (direction.getStepX() * widthOffset);
		double y = Math.min(beaconPos.getY() + 1, topY);
		double z = beaconPos.getZ() + 0.5 + (direction.getStepZ() * widthOffset);

		return new Vec3(x, y, z);
	}

	private InteractionResult attemptBeaconRespawn(PlayerEntry entry) {
		Vec3 spawn = this.getRespawnPos(entry);

		if (spawn == null) {
			return InteractionResult.FAIL;
		}

		entry.getPlayer().teleportTo(level, spawn.x(), spawn.y(), spawn.z(), Set.of(), 0, 0, true);

		return InteractionResult.SUCCESS;
	}

	private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
		return this.invulnerability > 0 || this.isGameEnding() ? EventResult.DENY : EventResult.PASS;
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		if (this.isGameEnding()) {
			BeaconBreakersActivePhase.spawn(this.level, this.map, this.config.getMapConfig(), player);
			return EventResult.DENY;
		}

		PlayerEntry entry = this.getEntryFromPlayer(player);

		if (entry != null) {
			if (this.level.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)) {
				this.gameSpace.getPlayers().sendMessage(player.getCombatTracker().getDeathMessage());
			}

			if (!this.config.shouldKeepInventory()) {
				entry.dropInventory(source);
			}

			if (this.attemptBeaconRespawn(entry) == InteractionResult.FAIL) {
				if (this.invulnerability > 0) {
					BeaconBreakersActivePhase.spawn(this.level, this.map, this.config.getMapConfig(), entry.getPlayer());
				} else {
					if (this.config.shouldKeepInventory()) {
						entry.dropInventory(source);
					}

					this.eliminate(entry);
				}
			}
		}

		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.setAirSupply(player.getMaxAirSupply());

		player.clearFire();
		player.getCombatTracker().recheckStatus();
		player.fallDistance = 0;
	
		return EventResult.DENY;
	}

	private void breakBeacon(PlayerEntry breaker, BlockPos pos, boolean explosion) {
		if (this.isGameEnding()) {
			return;
		}

		boolean found = false;

		for (TeamEntry entry : this.teams) {
			if (entry.getBeacon().isAt(pos)) {
				entry.setBeacon(BeaconPlacement.Broken.INSTANCE);

				this.gameSpace.getPlayers().playSound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1, 1);

				MutableComponent message = entry.getBeaconBreakMessage(breaker, explosion);
				this.gameSpace.getPlayers().sendMessage(message.withStyle(ChatFormatting.RED));

				found = true;
				break;
			}
		}

		if (found) {
			this.sidebar.update();
		}
	}

	private EventResult onBreakBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
		PlayerEntry entry = this.getEntryFromPlayer(player);
		if (entry == null) return EventResult.PASS;

		BlockState state = level.getBlockState(pos);
		TeamEntry team = entry.getTeam();

		if (team.getBeacon().isAt(pos)) {
			if (this.invulnerability > 0) {
				team.setBeacon(BeaconPlacement.Unplaced.INSTANCE);
				level.setBlockAndUpdate(pos, state.getFluidState().createLegacyBlock());

				entry.giveRespawnBeacon();
	
				this.sidebar.update();
			} else if (!this.config.shouldAllowSelfBreaking()) {
				player.sendSystemMessage(team.getCannotBreakOwnBeaconMessage().withStyle(ChatFormatting.RED), false);
			}

			return EventResult.DENY;
		}

		if (!state.is(Main.RESPAWN_BEACONS)) return EventResult.ALLOW;
			
		if (this.invulnerability > 0) {
			player.sendSystemMessage(Component.translatable("text.beaconbreakers.cannot_break_invulnerable_beacon").withStyle(ChatFormatting.RED), false);
			return EventResult.DENY;
		}

		this.breakBeacon(entry, pos, false);
		level.setBlockAndUpdate(pos, state.getFluidState().createLegacyBlock());

		return EventResult.DENY;		
	}

	private void afterBlockPlace(BlockPos pos, Level level, ServerPlayer player, ItemStack stack, BlockState state) {
		if (this.isGameEnding()) return;
		if (!state.is(Main.RESPAWN_BEACONS)) return;
		
		PlayerEntry entry = this.getEntryFromPlayer(player);
		if (entry == null) return;

		TeamEntry team = entry.getTeam();

		if (!(team.getBeacon() instanceof BeaconPlacement.Unplaced)) return;
		if (!this.map.getBox().isInside(pos)) {
			entry.sendMessage(Component.translatable("text.beaconbreakers.cannot_place_out_of_bounds_beacon").withStyle(ChatFormatting.RED), false);
			return;
		}

		team.setBeacon(new BeaconPlacement.Placed(pos));
		this.sidebar.update();
	}

	private InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hitResult) {
		if (!player.isSecondaryUseActive() || (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) {
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = this.level.getBlockState(pos);

			if (state.is(Main.RESPAWN_BEACONS)) {
				for (TeamEntry team : this.teams) {
					if (team.getBeacon().isAt(pos)) {
						PlayerEntry entry = this.getEntryFromPlayer(player);
						player.sendSystemMessage(team.getTattleMessageFor(entry), true);

						return InteractionResult.FAIL;
					}
				}
			}
		}

		return InteractionResult.PASS;
	}

	private EventResult onExplosionDetonated(Explosion explosion, List<BlockPos> blocksToDestroy) {
		ServerLevel level = explosion.level();

		LivingEntity causingEntity = explosion.getIndirectSourceEntity();
		PlayerEntry entry = causingEntity instanceof ServerPlayer player ? this.getEntryFromPlayer(player) : null;

		Iterator<BlockPos> iterator = blocksToDestroy.iterator();

		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();

			// Ignore non-respawn beacons
			BlockState state = level.getBlockState(pos);
			if (!state.is(Main.RESPAWN_BEACONS)) continue;

			// Prevent players from blowing up beacons during the invulnerability phase
			if (this.invulnerability > 0) {
				iterator.remove();
				continue;
			}

			// Prevent players from blowing up their own beacons
			if (!this.config.shouldAllowSelfBreaking() && entry != null && entry.getTeam().getBeacon().isAt(pos)) {
				iterator.remove();
				continue;
			}

			this.breakBeacon(entry, pos, true);
			level.setBlockAndUpdate(pos, state.getFluidState().createLegacyBlock());
		}

		return EventResult.PASS;
	}

	@SuppressWarnings("deprecation")
	private EventResult onThrowItem(ServerPlayer player, int slot, ItemStack stack) {
		if (stack.getItem() instanceof BlockItem blockItem) {
			Holder<Block> entry = blockItem.getBlock().builtInRegistryHolder();
			
			if (entry.is(Main.RESPAWN_BEACONS)) {
				return EventResult.DENY;
			}
		}

		return EventResult.PASS;
	}

	public GameSpace getGameSpace() {
		return this.gameSpace;
	}

	public BeaconBreakersMap getMap() {
		return this.map;
	}

	public BeaconBreakersConfig getConfig() {
		return this.config;
	}

	public int getInvulnerability() {
		return this.invulnerability;
	}

	public Iterable<TeamEntry> getTeams() {
		return this.teams;
	}

	public static Vec3 getSpawnPos(ServerLevel level, BeaconBreakersMap map, BeaconBreakersMapConfig mapConfig) {
		int x = mapConfig.getX() * 8;
		int z = mapConfig.getZ() * 8;

		int bottomY = level.getMinY();
		int maxY = Math.min(level.getMaxY(), bottomY + level.getLogicalHeight() - 1);

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);
		ChunkAccess chunk = level.getChunk(pos);

		int air = 0;

		while (pos.getY() > bottomY) {
			if (chunk.getBlockState(pos).isAir()) {
				air += 1;
			} else if (air > EntityTypes.PLAYER.getHeight()) {
				air = 0;
				break;
			} else {
				air = 0;
			}

			pos.move(Direction.DOWN);
		}

		if (pos.getY() == bottomY) {
			pos.setY(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
		}

		return new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
	}

	public static void spawn(ServerLevel level, BeaconBreakersMap map, BeaconBreakersMapConfig mapConfig, ServerPlayer player) {
		Vec3 spawnPos = BeaconBreakersActivePhase.getSpawnPos(level, map, mapConfig);
		player.teleportTo(level, spawnPos.x(), spawnPos.y(), spawnPos.z(), Set.of(), 0, 0, true);
	}
}
