package io.github.haykam821.beaconbreakers.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class BeaconBreakersMap {
	private final BeaconBreakersMapConfig mapConfig;
	private final BeaconBreakersChunkGenerator chunkGenerator;
	private final BoundingBox box;

	public BeaconBreakersMap(MinecraftServer server, BeaconBreakersMapConfig mapConfig, DimensionType dimensionType) {
		this.mapConfig = mapConfig;
		this.chunkGenerator = new BeaconBreakersChunkGenerator(server, this.mapConfig);

		int minY = dimensionType.minY();
		int maxY = minY + dimensionType.height();

		this.box = new BoundingBox(1, minY + 1, 1, mapConfig.getX() * 16 - 2, maxY - 1, mapConfig.getZ() * 16 - 2);
	}

	public BeaconBreakersChunkGenerator getChunkGenerator() {
		return this.chunkGenerator;
	}

	public BoundingBox getBox() {
		return this.box;
	}
}
