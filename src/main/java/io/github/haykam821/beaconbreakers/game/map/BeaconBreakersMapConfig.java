package io.github.haykam821.beaconbreakers.game.map;

import java.util.List;
import java.util.Optional;

import io.github.haykam821.beaconbreakers.mixin.MultiNoiseBiomeSourceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.core.registries.codec.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.ParameterPoint;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class BeaconBreakersMapConfig {
	public static final Codec<BeaconBreakersMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			RegistryFixedCodec.create(Registries.WORLD_PRESET).fieldOf("preset").forGetter(mapConfig -> mapConfig.worldPreset),
			ResourceKey.codec(Registries.LEVEL_STEM).optionalFieldOf("dimension_options", LevelStem.OVERWORLD).forGetter(mapConfig -> mapConfig.dimensionOptions),
			RegistryCodecs.holderSet(Registries.BIOME).optionalFieldOf("excluded_biomes").forGetter(mapConfig -> mapConfig.excludedBiomes),
			Codec.INT.optionalFieldOf("x", 16).forGetter(BeaconBreakersMapConfig::getX),
			Codec.INT.optionalFieldOf("z", 16).forGetter(BeaconBreakersMapConfig::getZ)
		).apply(instance, BeaconBreakersMapConfig::new);
	});

	private final Holder<WorldPreset> worldPreset;
	private final ResourceKey<LevelStem> dimensionOptions;
	private final Optional<HolderSet<Biome>> excludedBiomes;

	private final int x;
	private final int z;

	private ChunkGenerator chunkGenerator;

	public BeaconBreakersMapConfig(Holder<WorldPreset> worldPreset, ResourceKey<LevelStem> dimensionOptions, Optional<HolderSet<Biome>> excludedBiomes, int x, int z) {
		this.worldPreset = worldPreset;
		this.dimensionOptions = dimensionOptions;
		this.excludedBiomes = excludedBiomes;

		this.x = x;
		this.z = z;
	}

	public LevelStem getDimensionOptions() {
		WorldDimensions registryHolder = this.worldPreset.value().createWorldDimensions();
		return registryHolder.dimensions().get(this.dimensionOptions);
	}

	public int getX() {
		return this.x;
	}

	public int getZ() {
		return this.z;
	}

	public ChunkGenerator getChunkGenerator() {
		if (this.chunkGenerator == null) {
			this.chunkGenerator = this.createChunkGenerator();
		}

		return this.chunkGenerator;
	}

	private boolean isIncludedBiome(Pair<ParameterPoint, Holder<Biome>> pair) {
		return this.excludedBiomes.isEmpty() || !this.excludedBiomes.get().contains(pair.getSecond());
	}

	private ChunkGenerator createChunkGenerator() {
		LevelStem dimensionOptions = this.getDimensionOptions();

		if (this.excludedBiomes.isPresent()) {
			if (dimensionOptions.generator() instanceof NoiseBasedChunkGenerator noiseChunkGenerator) {
				if (noiseChunkGenerator.getBiomeSource() instanceof MultiNoiseBiomeSource biomeSource) {
					List<Pair<ParameterPoint, Holder<Biome>>> entries = ((MultiNoiseBiomeSourceAccessor) biomeSource).callParameters()
						.values()
						.stream()
						.filter(this::isIncludedBiome)
						.toList();

					MultiNoiseBiomeSource newBiomeSource = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(entries));

					return new NoiseBasedChunkGenerator(newBiomeSource, noiseChunkGenerator.generatorSettings());
				}

				throw new IllegalArgumentException("Cannot exclude biomes from unsupported biome source");
			}

			throw new IllegalArgumentException("Cannot exclude biomes from unsupported chunk generator");
		}

		return dimensionOptions.generator();
	}
}
