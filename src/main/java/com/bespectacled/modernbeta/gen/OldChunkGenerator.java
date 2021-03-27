package com.bespectacled.modernbeta.gen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import com.bespectacled.modernbeta.ModernBeta;
import com.bespectacled.modernbeta.biome.BiomeType;
import com.bespectacled.modernbeta.biome.CaveBiomeType;
import com.bespectacled.modernbeta.biome.OldBiomeSource;
import com.bespectacled.modernbeta.biome.beta.BetaBiomes;
import com.bespectacled.modernbeta.carver.IOldCaveCarver;
import com.bespectacled.modernbeta.feature.OldFeatures;
import com.bespectacled.modernbeta.gen.provider.AbstractChunkProvider;
import com.bespectacled.modernbeta.gen.provider.IndevChunkProvider;
import com.bespectacled.modernbeta.mixin.MixinChunkGeneratorInvoker;
import com.bespectacled.modernbeta.mixin.MixinConfiguredCarverAccessor;
import com.bespectacled.modernbeta.structure.OldStructures;
import com.bespectacled.modernbeta.util.BlockStates;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import net.minecraft.world.gen.feature.ConfiguredStructureFeatures;
import net.minecraft.world.gen.feature.StructureFeature;

public class OldChunkGenerator extends NoiseChunkGenerator {
    public static final Codec<OldChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance
        .group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            Codec.LONG.fieldOf("seed").stable().forGetter(generator -> generator.worldSeed),
            OldGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings))
        .apply(instance, instance.stable(OldChunkGenerator::new)));
    
    private static final int OCEAN_Y_CUT_OFF = 40;
    
    private final Random random;
    
    private final OldGeneratorSettings settings;
    private final WorldType worldType;
    private final boolean generateOceans;
    
    private final OldBiomeSource biomeSource;
    private final AbstractChunkProvider chunkProvider;
    
    public OldChunkGenerator(BiomeSource biomeSource, long seed, OldGeneratorSettings settings) {
        super(biomeSource, seed, settings.generatorSettings);
        
        this.random = new Random(seed);
        
        this.biomeSource = (OldBiomeSource)biomeSource;
        this.settings = settings;
        
        this.worldType = WorldType.getWorldType(settings.providerSettings);
        this.chunkProvider = this.worldType.createChunkProvider(seed, settings);
        
        this.generateOceans = settings.providerSettings.contains("generateOceans") ? settings.providerSettings.getBoolean("generateOceans") : false;
    }

    public static void register() {
        Registry.register(Registry.CHUNK_GENERATOR, ModernBeta.createId("old"), CODEC);
    }
    
    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return OldChunkGenerator.CODEC;
    }
    
    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, StructureAccessor accessor, Chunk chunk) {   
        return CompletableFuture.<Chunk>supplyAsync(
            () -> this.chunkProvider.provideChunk(accessor, chunk, this.biomeSource), Util.getMainWorkerExecutor()
        );
    }
        
    @Override
    public void buildSurface(ChunkRegion region, Chunk chunk) {
        this.chunkProvider.provideSurface(region, chunk, this.biomeSource);
        
        if (this.generateOceans) {
            OldGeneratorUtil.replaceOceansInChunk(chunk, this.biomeSource, this.getWorldHeight(), this.getMinimumY(), this.getSeaLevel(), OCEAN_Y_CUT_OFF);
        }
    }

    @Override
    public void generateFeatures(ChunkRegion region, StructureAccessor accessor) {
        OldFeatures.OLD_FANCY_OAK.chunkReset();
        
        ChunkPos chunkPos = region.getCenterPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        
        Biome biome = OldGeneratorUtil.getOceanBiome(region.getChunk(chunkPos.x, chunkPos.z), this, biomeSource, generateOceans, this.getSeaLevel());
        
        // Use edge biome for feature population for Indev chunks outside of level area
        if (this.chunkProvider.skipChunk(chunkPos.x, chunkPos.z) && this.chunkProvider instanceof IndevChunkProvider)
            biome = this.biomeSource.getEdgeBiome();
        
        // TODO: Remove chunkRandom at some point
        ChunkRandom chunkRandom = new ChunkRandom();
        long popSeed = chunkRandom.setPopulationSeed(region.getSeed(), startX, startZ);

        try {
            biome.generateFeatureStep(accessor, this, region, popSeed, chunkRandom, new BlockPos(startX, region.getBottomY(), startZ));
        } catch (Exception exception) {
            CrashReport report = CrashReport.create(exception, "Biome decoration");
            report.addElement("Generation").add("CenterX", chunkPos.x).add("CenterZ", chunkPos.z).add("Seed", popSeed).add("Biome", biome);
            throw new CrashException(report);
        }
    }
    
    @Override
    public void carve(long seed, BiomeAccess access, Chunk chunk, GenerationStep.Carver genCarver) {
        if (this.chunkProvider instanceof IndevChunkProvider) return; // Skip since Indev Provider has its own carver.
        
        BiomeAccess biomeAcc = access.withSource(this.biomeSource);
        ChunkPos chunkPos = chunk.getPos();

        int mainChunkX = chunkPos.x;
        int mainChunkZ = chunkPos.z;

        Biome biome = OldGeneratorUtil.getOceanBiome(chunk, this, this.biomeSource, this.generateOceans, this.getSeaLevel());
        GenerationSettings genSettings = biome.getGenerationSettings();
        CarverContext heightContext = new CarverContext(this);
        
        BitSet bitSet = ((ProtoChunk)chunk).getOrCreateCarvingMask(genCarver);

        this.random.setSeed(seed);
        long l = (this.random.nextLong() / 2L) * 2L + 1L;
        long l1 = (this.random.nextLong() / 2L) * 2L + 1L;

        for (int chunkX = mainChunkX - 8; chunkX <= mainChunkX + 8; ++chunkX) {
            for (int chunkZ = mainChunkZ - 8; chunkZ <= mainChunkZ + 8; ++chunkZ) {
                List<Supplier<ConfiguredCarver<?>>> carverList = genSettings.getCarversForStep(genCarver);
                ListIterator<Supplier<ConfiguredCarver<?>>> carverIterator = carverList.listIterator();
                ChunkPos caveChunkPos = new ChunkPos(chunkX, chunkZ);

                while (carverIterator.hasNext()) {
                    ConfiguredCarver<?> configuredCarver = carverIterator.next().get();
                    Carver<?> carver = ((MixinConfiguredCarverAccessor)configuredCarver).getCarver();
                    
                    this.random.setSeed((long) chunkX * l + (long) chunkZ * l1 ^ seed);
                    
                    // Special case for old Beta carvers.
                    if (carver instanceof IOldCaveCarver) {
                        ((IOldCaveCarver)carver).carve(heightContext, chunk, this.random, chunkX, chunkZ, mainChunkX, mainChunkZ);
                        
                    } else if (configuredCarver.shouldCarve(random)) {
                        configuredCarver.carve(heightContext, chunk, biomeAcc::getBiome, this.random, this.getSeaLevel(), caveChunkPos, bitSet);

                    }
                }
            }
        }
    }
    
    @Override
    public void setStructureStarts(
        DynamicRegistryManager dynamicRegistryManager, 
        StructureAccessor structureAccessor,   
        Chunk chunk, 
        StructureManager structureManager, 
        long seed
    ) {
        ChunkPos chunkPos = chunk.getPos();
        Biome biome = OldGeneratorUtil.getOceanBiome(chunk, this, this.biomeSource, this.generateOceans, this.getSeaLevel());
        
        // Use edge biome for feature population for Indev chunks outside of level area
        if (this.chunkProvider.skipChunk(chunkPos.x, chunkPos.z) && this.chunkProvider instanceof IndevChunkProvider)
            biome = this.biomeSource.getEdgeBiome();

        ((MixinChunkGeneratorInvoker)this).invokeSetStructureStart(
            ConfiguredStructureFeatures.STRONGHOLD, 
            dynamicRegistryManager, 
            structureAccessor, 
            chunk,
            structureManager, 
            seed, 
            biome
        );
        
        for (final Supplier<ConfiguredStructureFeature<?, ?>> supplier : biome.getGenerationSettings()
                .getStructureFeatures()) {
            ((MixinChunkGeneratorInvoker)this).invokeSetStructureStart(
                supplier.get(),
                dynamicRegistryManager, 
                structureAccessor,
                chunk, 
                structureManager,
                seed,
                biome
            );
        }
    }
    
    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world) {
        return this.chunkProvider.getHeight(x, z, type);
    }
    
    /*
    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world) {
        int bottomY = Math.max(this.settings.generatorSettings.get().getGenerationShapeConfig().getMinimumY(), world.getBottomY());
        int topY = Math.min(this.settings.generatorSettings.get().getGenerationShapeConfig().getMinimumY() + this.settings.generatorSettings.get().getGenerationShapeConfig().getHeight(), world.getTopY());
        
        //int noiseBottomY = MathHelper.floorDiv(bottomY, this.chunkProvider.getVerticalNoiseResolution());
        int noiseTopY = MathHelper.floorDiv(topY - bottomY, this.chunkProvider.getVerticalNoiseResolution());
        
        if (noiseTopY <= 0) {
            return new VerticalBlockSample(bottomY, new BlockState[0]);
        }
        
        BlockState[] states = new BlockState[noiseTopY * this.chunkProvider.getVerticalNoiseResolution()];
        int sampledHeight = this.chunkProvider.getHeight(x, z, null);
        
        for (int y = 0; y < noiseTopY * this.chunkProvider.getVerticalNoiseResolution(); ++y) {
            y += bottomY;
            
            BlockState state = BlockStates.AIR;
            if (y < sampledHeight) {
                state = BlockStates.STONE;
            }
            
            states[y] = state;
        }
        
        return new VerticalBlockSample(bottomY, states);
    }*/
    
    @Override
    public BlockPos locateStructure(ServerWorld world, StructureFeature<?> feature, BlockPos center, int radius, boolean skipExistingChunks) {
        if (!this.generateOceans)
            if (feature.equals(StructureFeature.OCEAN_RUIN) || 
                feature.equals(StructureFeature.SHIPWRECK) || 
                feature.equals(StructureFeature.BURIED_TREASURE) ||
                feature.equals(OldStructures.OCEAN_SHRINE_STRUCTURE)) {
                return null;
            }

        return super.locateStructure(world, feature, center, radius, skipExistingChunks);
    }
    
    @Override
    public List<SpawnSettings.SpawnEntry> getEntitySpawnList(Biome biome, StructureAccessor structureAccessor, SpawnGroup spawnGroup, BlockPos blockPos) {
        if (spawnGroup == SpawnGroup.MONSTER) {
            if (structureAccessor.getStructureAt(blockPos, false, OldStructures.OCEAN_SHRINE_STRUCTURE).hasChildren()) {
                return OldStructures.OCEAN_SHRINE_STRUCTURE.getMonsterSpawns();
            }
        }

        return super.getEntitySpawnList(biome, structureAccessor, spawnGroup, blockPos);
    }

    @Override
    public int getWorldHeight() {
        // TODO: Causes issue with YOffset.BelowTop decorator (i.e. ORE_COAL_UPPER), find some workaround.
        //return chunkProvider.getWorldHeight();
        return 384;
    }
    
    @Override
    public int getMinimumY() {
        return this.getChunkProvider().getMinimumY();
        //return -64;
    }

    @Override
    public int getSeaLevel() {
        return chunkProvider.getSeaLevel();
    }
    
    @Override
    public ChunkGenerator withSeed(long seed) {
        return new OldChunkGenerator(this.biomeSource.withSeed(seed), seed, this.settings);
    }
    
    public WorldType getWorldType() {
        return this.worldType;
    }
    
    public AbstractChunkProvider getChunkProvider() {
        return this.chunkProvider;
    }
    
    public NbtCompound getProviderSettings() {
        return this.settings.providerSettings;
    }
    
    public static void export() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path dir = Paths.get("..\\src\\main\\resources\\data\\modern_beta\\dimension");
        
        NbtCompound chunkSettings = OldGeneratorSettings.createInfSettings(WorldType.BETA);
        NbtCompound biomeSettings = OldGeneratorSettings.createBiomeSettings(BiomeType.BETA, CaveBiomeType.VANILLA, BetaBiomes.FOREST_ID);
        OldGeneratorSettings generatorSettings = new OldGeneratorSettings(() -> OldGeneratorSettings.BETA_GENERATOR_SETTINGS, chunkSettings);
        
        OldBiomeSource biomeSource = new OldBiomeSource(0, BuiltinRegistries.BIOME, biomeSettings);
        OldChunkGenerator chunkGenerator = new OldChunkGenerator(biomeSource, 0, generatorSettings);
        Function<OldChunkGenerator, DataResult<JsonElement>> toJson = JsonOps.INSTANCE.withEncoder(OldChunkGenerator.CODEC);
        
        try {
            JsonElement json = toJson.apply(chunkGenerator).result().get();
            Files.write(dir.resolve(ModernBeta.createId("old").getPath() + ".json"), gson.toJson(json).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            ModernBeta.LOGGER.error("[Modern Beta] Couldn't serialize old chunk generator!");
            e.printStackTrace();
        }
    }
    
}
