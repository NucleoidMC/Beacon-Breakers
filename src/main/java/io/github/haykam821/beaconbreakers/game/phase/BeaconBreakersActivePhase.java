package io.github.haykam821.beaconbreakers.game.phase;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import io.github.haykam821.beaconbreakers.Main;
import io.github.haykam821.beaconbreakers.game.BeaconBreakersConfig;
import io.github.haykam821.beaconbreakers.game.BeaconBreakersSidebar;
import io.github.haykam821.beaconbreakers.game.InvulnerabilityTimerBar;
import io.github.haykam821.beaconbreakers.game.PlayerEntry;
import io.github.haykam821.beaconbreakers.game.event.AfterBlockPlaceListener;
import io.github.haykam821.beaconbreakers.game.map.BeaconBreakersMap;
import io.github.haykam821.beaconbreakers.game.map.BeaconBreakersMapConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.BreakBlockListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.PlayerRemoveListener;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;

public class BeaconBreakersActivePhase {
	private final GameSpace gameSpace;
	private final ServerWorld world;
	private final BeaconBreakersMap map;
	private final BeaconBreakersConfig config;
	private final Set<PlayerEntry> players;
	private final InvulnerabilityTimerBar bar;
	private final BeaconBreakersSidebar sidebar;
	private boolean singleplayer;
	private int invulnerability;

	public BeaconBreakersActivePhase(GameSpace gameSpace, GlobalWidgets widgets, BeaconBreakersMap map, BeaconBreakersConfig config, Set<ServerPlayerEntity> players) {
		this.gameSpace = gameSpace;
		this.world = gameSpace.getWorld();
		this.map = map;
		this.config = config;

		this.sidebar = new BeaconBreakersSidebar(widgets, this);
		this.bar = new InvulnerabilityTimerBar(this, widgets);
		this.players = players.stream().map(player -> {
			return new PlayerEntry(player);
		}).collect(Collectors.toSet());

		this.invulnerability = this.config.getInvulnerability();
	}

	public static void open(GameSpace gameSpace, BeaconBreakersMap map, BeaconBreakersConfig config) {
		gameSpace.openGame(game -> {
			GlobalWidgets widgets = new GlobalWidgets(game);
			Set<ServerPlayerEntity> players = Sets.newHashSet(gameSpace.getPlayers());
			BeaconBreakersActivePhase active = new BeaconBreakersActivePhase(gameSpace, widgets, map, config, players);

			game.setRule(GameRule.BLOCK_DROPS, RuleResult.ALLOW);
			game.setRule(GameRule.CRAFTING, RuleResult.ALLOW);
			game.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
			game.setRule(GameRule.HUNGER, RuleResult.ALLOW);
			game.setRule(GameRule.PORTALS, RuleResult.DENY);
			game.setRule(GameRule.PVP, RuleResult.ALLOW);

			// Listeners
			game.on(AfterBlockPlaceListener.EVENT, active::afterBlockPlace);
			game.on(BreakBlockListener.EVENT, active::onBreakBlock);
			game.on(GameOpenListener.EVENT, active::open);
			game.on(GameTickListener.EVENT, active::tick);
			game.on(PlayerAddListener.EVENT, active::addPlayer);
			game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
			game.on(PlayerRemoveListener.EVENT, active::removePlayer);
		});
	}

	private void open() {
		this.singleplayer = this.players.size() == 1;

		for (PlayerEntry entry : this.players) {
			ServerPlayerEntity player = entry.getPlayer();

			player.setGameMode(GameMode.SURVIVAL);
			player.giveItemStack(new ItemStack(Main.RESPAWN_BEACONS.getRandom(this.world.getRandom())));

			player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, this.invulnerability, 1, true, false));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, this.invulnerability, 127, true, false));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, this.invulnerability, 127, true, false));

			BeaconBreakersActivePhase.spawn(this.world, this.map, this.config.getMapConfig(), player);
		}

		this.sidebar.update();
	}
	
	private void tick() {
		if (this.invulnerability > 0) {
			this.bar.tick();

			this.invulnerability -= 1;
			if (this.invulnerability == 0) {
				this.gameSpace.getPlayers().sendSound(SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1, 1);
				this.gameSpace.getPlayers().sendMessage(new TranslatableText("text.beaconbreakers.invulnerability.ended").formatted(Formatting.GOLD));

				this.bar.remove();
			}
		}

		Iterator<PlayerEntry> iterator = this.players.iterator();
		while (iterator.hasNext()) {
			PlayerEntry entry = iterator.next();
			ServerPlayerEntity player = entry.getPlayer();

			if (!this.map.getBox().contains(player.getBlockPos())) {
				this.setSpectator(player);
				this.sendEliminateMessage(entry);
				iterator.remove();
				this.sidebar.update();
			}
		}
	
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;

			this.gameSpace.getPlayers().sendMessage(this.getEndingMessage().formatted(Formatting.GOLD));
			this.gameSpace.close(GameCloseReason.FINISHED);
		}
	}
	
	private MutableText getEndingMessage() {
		if (this.players.size() == 1) {
			ServerPlayerEntity winner = this.players.iterator().next().getPlayer();
			return new TranslatableText("text.beaconbreakers.win", winner.getDisplayName());
		}
		return new TranslatableText("text.beaconbreakers.win.none");
	}

	private void setSpectator(PlayerEntity player) {
		player.setGameMode(GameMode.SPECTATOR);
	}

	private void sendEliminateMessage(PlayerEntry entry) {
		this.gameSpace.getPlayers().sendMessage(new TranslatableText("text.beaconbreakers.eliminate", entry.getPlayer().getDisplayName()).formatted(Formatting.RED));
	}

	private void eliminate(PlayerEntry entry) {
		this.setSpectator(entry.getPlayer());
		this.sendEliminateMessage(entry);
		this.players.remove(entry);
		this.sidebar.update();
	}

	private PlayerEntry getEntryFromPlayer(ServerPlayerEntity player) {
		for (PlayerEntry entry : this.players) {
			if (player.equals(entry.getPlayer())) {
				return entry;
			}
		}
		return null;
	}

	private void addPlayer(ServerPlayerEntity player) {
		if (this.getEntryFromPlayer(player) == null) {
			this.setSpectator(player);
		}
	}

	private void removePlayer(ServerPlayerEntity player) {
		Iterator<PlayerEntry> iterator = this.players.iterator();
		while (iterator.hasNext()) {
			PlayerEntry entry = iterator.next();

			if (player.equals(entry.getPlayer())) {
				this.setSpectator(player);
				this.sendEliminateMessage(entry);
				iterator.remove();
				this.sidebar.update();
			}
		}
	}

	private Vec3d getRespawnPos(BlockPos beaconPos) {
		Optional<Vec3d> spawnOptional = RespawnAnchorBlock.findRespawnPosition(EntityType.PLAYER, world, beaconPos);
		if (spawnOptional.isPresent()) {
			Vec3d spawn = spawnOptional.get();
			if (spawn.getY() <= 255) {
				return spawn;
			}
		}
	
		return new Vec3d(beaconPos.getX() + 0.5, beaconPos.getY(), beaconPos.getZ() + 0.5);
	}

	private ActionResult attemptBeaconRespawn(PlayerEntry entry) {
		BlockPos beaconPos = entry.getBeaconPos();
		if (beaconPos == null) {
			return ActionResult.FAIL;
		}

		BlockState beaconState = this.world.getBlockState(beaconPos);
		if (!beaconState.isIn(Main.RESPAWN_BEACONS)) {
			return ActionResult.FAIL;
		}

		Vec3d spawn = this.getRespawnPos(beaconPos); 
		entry.getPlayer().teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), 0, 0);;

		return ActionResult.SUCCESS;
	}

	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		if (!this.config.shouldKeepInventory()) {
			player.vanishCursedItems();
			player.inventory.dropAll();

			player.dropXp();
			player.dropShoulderEntities();
		}

		PlayerEntry entry = this.getEntryFromPlayer(player);
		if (entry != null) {
			if (this.world.getGameRules().getBoolean(GameRules.SHOW_DEATH_MESSAGES)) {
				this.gameSpace.getPlayers().sendMessage(player.getDamageTracker().getDeathMessage());
			}

			if (this.attemptBeaconRespawn(entry) == ActionResult.FAIL) {
				if (this.invulnerability > 0) {
					BeaconBreakersActivePhase.spawn(this.world, this.map, this.config.getMapConfig(), entry.getPlayer());
				} else {
					this.eliminate(entry);
				}
			}
		}

		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);

		player.extinguish();
		player.getDamageTracker().update();
	
		return ActionResult.FAIL;
	}

	private void breakBeacon(PlayerEntry breaker, BlockPos pos) {
		for (PlayerEntry entry : this.players) {
			if (pos.equals(entry.getBeaconPos())) {
				entry.setBeaconBroken();

				this.gameSpace.getPlayers().sendSound(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1, 1);
				this.gameSpace.getPlayers().sendMessage(new TranslatableText("text.beaconbreakers.beacon_break", entry.getPlayer().getDisplayName(), breaker.getPlayer().getDisplayName()).formatted(Formatting.RED));

				this.sidebar.update();
				return;
			}
		}

		this.sidebar.update();
	}

	private ActionResult onBreakBlock(ServerPlayerEntity player, BlockPos pos) {
		PlayerEntry entry = this.getEntryFromPlayer(player);
		if (entry == null) return ActionResult.PASS;

		if (!this.config.shouldAllowSelfBreaking() && pos.equals(entry.getBeaconPos())) {
			player.sendMessage(new TranslatableText("text.beaconbreakers.cannot_break_own_beacon").formatted(Formatting.RED), false);
			return ActionResult.FAIL;
		}

		BlockState state = player.getEntityWorld().getBlockState(pos);
		if (!state.isIn(Main.RESPAWN_BEACONS)) return ActionResult.SUCCESS;
			
		if (this.invulnerability > 0) {
			player.sendMessage(new TranslatableText("text.beaconbreakers.cannot_break_invulnerable_beacon").formatted(Formatting.RED), false);
			return ActionResult.FAIL;
		}

		this.breakBeacon(entry, pos);
		player.getEntityWorld().setBlockState(pos, state.getFluidState().getBlockState());

		return ActionResult.FAIL;		
	}

	private void afterBlockPlace(BlockPos pos, World world, ServerPlayerEntity player, ItemStack stack, BlockState state) {
		if (!state.isIn(Main.RESPAWN_BEACONS)) return;
		
		PlayerEntry entry = this.getEntryFromPlayer(player);
		if (entry == null) return;
		
		if (entry.getBeaconPos() != null) return;
		if (!this.map.getBox().contains(pos)) {
			entry.getPlayer().sendMessage(new TranslatableText("text.beaconbreakers.cannot_place_out_of_bounds_beacon").formatted(Formatting.RED), false);
			return;
		}

		entry.setBeaconPos(pos);
		this.sidebar.update();
	}

	public GameSpace getGameSpace() {
		return this.gameSpace;
	}

	public BeaconBreakersConfig getConfig() {
		return this.config;
	}

	public int getInvulnerability() {
		return this.invulnerability;
	}

	public Set<PlayerEntry> getPlayers() {
		return this.players;
	}

	public static void spawn(ServerWorld world, BeaconBreakersMap map, BeaconBreakersMapConfig mapConfig, ServerPlayerEntity player) {
		int x = mapConfig.getX() * 8;
		int z = mapConfig.getZ() * 8;

		int surfaceY = map.getChunkGenerator().getHeight(x, z, Heightmap.Type.WORLD_SURFACE);
		player.teleport(world, x + 0.5, surfaceY, z + 0.5, 0, 0);
	}
}
