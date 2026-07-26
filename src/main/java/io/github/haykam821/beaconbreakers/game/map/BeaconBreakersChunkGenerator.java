package io.github.haykam821.beaconbreakers.game.map;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import xyz.nucleoid.fantasy.util.ChunkGeneratorSettingsProvider;
import xyz.nucleoid.plasmid.api.game.level.generator.GameChunkGenerator;

public final class BeaconBreakersChunkGenerator extends GameChunkGenerator implements ChunkGeneratorSettingsProvider {
	private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

	private final BeaconBreakersMapConfig mapConfig;
	private final ChunkGenerator chunkGenerator;

	public BeaconBreakersChunkGenerator(MinecraftServer server, BeaconBreakersMapConfig mapConfig) {
		super(mapConfig.getChunkGenerator().getBiomeSource());
		this.mapConfig = mapConfig;

		this.chunkGenerator = mapConfig.getChunkGenerator();
	}

	@Override
	public NoiseGeneratorSettings getSettings() {
		if (this.chunkGenerator instanceof NoiseBasedChunkGenerator noiseChunkGenerator) {
			return noiseChunkGenerator.generatorSettings().value();
		}

		return null;
	}

	private boolean isChunkPosWithinArea(ChunkPos chunkPos) {
		return chunkPos.x() >= 0 && chunkPos.z() >= 0 && chunkPos.x() < this.mapConfig.getX() && chunkPos.z() < this.mapConfig.getZ();
	}

	private boolean isChunkWithinArea(ChunkAccess chunk) {
		return this.isChunkPosWithinArea(chunk.getPos());
	}

	@Override
	public CompletableFuture<ChunkAccess> createBiomes(RandomState noiseConfig, Blender blender, StructureManager structures, ChunkAccess chunk) {
		if (this.isChunkWithinArea(chunk)) {
			return this.chunkGenerator.createBiomes(noiseConfig, blender, structures, chunk);
		} else {
			return super.createBiomes(noiseConfig, blender, structures, chunk);
		}
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState noiseConfig, StructureManager structures, ChunkAccess chunk) {
		if (this.isChunkWithinArea(chunk)) {
			return this.chunkGenerator.fillFromNoise(blender, noiseConfig, structures, chunk);
		}
		return super.fillFromNoise(blender, noiseConfig, structures, chunk);
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState noiseConfig, ChunkAccess chunk) {
		if (this.isChunkWithinArea(chunk)) {
			this.chunkGenerator.buildSurface(region, structures, noiseConfig, chunk);
		}
	}

	@Override
	public BiomeSource getBiomeSource() {
		return this.chunkGenerator.getBiomeSource();
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel world, ChunkAccess chunk, StructureManager structures) {
		ChunkPos chunkPos = chunk.getPos();
		if (!this.isChunkPosWithinArea(chunkPos)) return;

		this.chunkGenerator.applyBiomeDecoration(world, chunk, structures);

		int chunkX = chunkPos.x();
		int chunkZ = chunkPos.z();
	
		BlockPos pos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

		int bottomY = chunk.getMinY();
		int topY = chunk.getMaxY();

		// Top
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				mutablePos.set(x + pos.getX(), topY, z + pos.getZ());
				chunk.setBlockState(mutablePos, BARRIER);
			}
		}

		// North
		if (chunkZ == 0) {
			for (int x = 0; x < 16; x++) {
				for (int y = bottomY; y < topY; y++) {
					mutablePos.set(x + pos.getX(), y, pos.getZ());
					chunk.setBlockState(mutablePos, BARRIER);
				}
			}
		}

		// East
		if (chunkX == this.mapConfig.getX() - 1) {
			for (int z = 0; z < 16; z++) {
				for (int y = bottomY; y < topY; y++) {
					mutablePos.set(pos.getX() + 15, y, z + pos.getZ());
					chunk.setBlockState(mutablePos, BARRIER);
				}
			}
		}

		// South
		if (chunkZ == this.mapConfig.getZ() - 1) {
			for (int x = 0; x < 16; x++) {
				for (int y = bottomY; y < topY; y++) {
					mutablePos.set(x + pos.getX(), y, pos.getZ() + 15);
					chunk.setBlockState(mutablePos, BARRIER);
				}
			}
		}

		// West
		if (chunkX == 0) {
			for (int z = 0; z < 16; z++) {
				for (int y = bottomY; y < topY; y++) {
					mutablePos.set(pos.getX(), y, z + pos.getZ());
					chunk.setBlockState(mutablePos, BARRIER);
				}
			}
		}
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState noiseConfig, BiomeManager access, StructureManager structures, ChunkAccess chunk) {
		if (this.isChunkWithinArea(chunk)) {
			this.chunkGenerator.applyCarvers(region, seed, noiseConfig, access, structures, chunk);
		}
	}

	@Override
	public int getSeaLevel() {
		return this.chunkGenerator.getSeaLevel();
	}

	@Override
	public int getMinY() {
		return this.chunkGenerator.getMinY();
	}

	@Override
	public int getGenDepth() {
		return this.chunkGenerator.getGenDepth();
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor world, RandomState noiseConfig) {
		if (this.isChunkPosWithinArea(new ChunkPos(x >> 4, z >> 4))) {
			return this.chunkGenerator.getBaseHeight(x, z, heightmapType, world, noiseConfig);
		}
		return super.getBaseHeight(x, z, heightmapType, world, noiseConfig);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig) {
		if (this.isChunkPosWithinArea(new ChunkPos(x >> 4, z >> 4))) {
			return this.chunkGenerator.getBaseColumn(x, z, world, noiseConfig);
		}
		return super.getBaseColumn(x, z, world, noiseConfig);
	}
}
