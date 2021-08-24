package io.github.haykam821.beaconbreakers.game.map;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class BeaconBreakersMapConfig {
	public static final Codec<BeaconBreakersMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			ChunkGenerator.CODEC.optionalFieldOf("chunk_generator").forGetter(BeaconBreakersMapConfig::getChunkGenerator),
			Identifier.CODEC.fieldOf("settings").forGetter(BeaconBreakersMapConfig::getChunkGeneratorSettingsId),
			Codec.INT.optionalFieldOf("x", 16).forGetter(BeaconBreakersMapConfig::getX),
			Codec.INT.optionalFieldOf("z", 16).forGetter(BeaconBreakersMapConfig::getZ)
		).apply(instance, BeaconBreakersMapConfig::new);
	});

	private final Optional<ChunkGenerator> chunkGenerator;
	private final Identifier chunkGeneratorSettingsId;
	private final int x;
	private final int z;

	public BeaconBreakersMapConfig(Optional<ChunkGenerator> chunkGenerator, Identifier chunkGeneratorSettingsId, int x, int z) {
		this.chunkGenerator = chunkGenerator;
		this.chunkGeneratorSettingsId = chunkGeneratorSettingsId;
		this.x = x;
		this.z = z;
	}

	public Optional<ChunkGenerator> getChunkGenerator() {
		return this.chunkGenerator;
	}

	public Identifier getChunkGeneratorSettingsId() {
		return this.chunkGeneratorSettingsId;
	}

	public int getX() {
		return this.x;
	}

	public int getZ() {
		return this.z;
	}
}
