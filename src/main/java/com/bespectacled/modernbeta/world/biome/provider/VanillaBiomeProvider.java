package com.bespectacled.modernbeta.world.biome.provider;

import java.util.ArrayList;
import java.util.List;

import com.bespectacled.modernbeta.api.world.biome.ClimateBiomeProvider;
import com.bespectacled.modernbeta.world.biome.provider.climate.VanillaClimateSampler;
import com.bespectacled.modernbeta.world.biome.vanilla.VanillaBiomeSource;
import com.bespectacled.modernbeta.world.biome.vanilla.VanillaBiomeSourceCreator;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

public class VanillaBiomeProvider extends ClimateBiomeProvider {
    private final VanillaBiomeSource vanillaBiomeSource;
    private final VanillaBiomeSource oceanBiomeSource;
    private final VanillaBiomeSource deepOceanBiomeSource;
    
    public VanillaBiomeProvider(long seed, NbtCompound settings, Registry<Biome> biomeRegistry) {
        super(seed, settings, biomeRegistry, new VanillaClimateSampler(VanillaBiomeSourceCreator.buildLandBiomeSource(biomeRegistry, seed)));
        
        this.vanillaBiomeSource = ((VanillaClimateSampler)this.getClimateSampler()).getBiomeSource();
        this.oceanBiomeSource = VanillaBiomeSourceCreator.buildOceanBiomeSource(biomeRegistry, seed);
        this.deepOceanBiomeSource = VanillaBiomeSourceCreator.buildDeepOceanBiomeSource(biomeRegistry, seed);
    }

    @Override
    public Biome getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
        return this.vanillaBiomeSource.getBiome(biomeX, biomeY, biomeZ);
    }
    
    @Override
    public Biome getOceanBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
        return this.oceanBiomeSource.getBiome(biomeX, biomeY, biomeZ);
    }
    
    @Override
    public Biome getDeepOceanBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
        return this.deepOceanBiomeSource.getBiome(biomeX, biomeY, biomeZ);
    }
    
    @Override
    public List<Biome> getBiomesForRegistry() {
        List<Biome> biomes = new ArrayList<>();
        biomes.addAll(this.vanillaBiomeSource.getBiomes());
        biomes.addAll(this.oceanBiomeSource.getBiomes());
        biomes.addAll(this.deepOceanBiomeSource.getBiomes());
        
        return biomes;
    }
}
