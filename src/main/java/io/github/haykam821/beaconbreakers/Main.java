package io.github.haykam821.beaconbreakers;

import io.github.haykam821.beaconbreakers.game.BeaconBreakersConfig;
import io.github.haykam821.beaconbreakers.game.phase.BeaconBreakersWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.game.GameTypes;

public class Main implements ModInitializer {
	private static final String MOD_ID = "beaconbreakers";

	private static final Identifier RESPAWN_BEACONS_ID = Main.identifier("respawn_beacons");
	public static final TagKey<Block> RESPAWN_BEACONS = TagKey.create(Registries.BLOCK, RESPAWN_BEACONS_ID);

	private static final Identifier BEACON_BREAKERS_ID = Main.identifier("beacon_breakers");
	public static final GameType<BeaconBreakersConfig> BEACON_BREAKERS_TYPE = GameTypes.register(BEACON_BREAKERS_ID, BeaconBreakersConfig.CODEC, BeaconBreakersWaitingPhase::open);

	@Override
	public void onInitialize() {
		return;
	}

	public static Identifier identifier(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
