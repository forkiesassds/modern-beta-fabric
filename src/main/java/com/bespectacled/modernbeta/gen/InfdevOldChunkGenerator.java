package com.bespectacled.modernbeta.gen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biome.Category;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

import com.bespectacled.modernbeta.ModernBeta;
import com.bespectacled.modernbeta.biome.OldBiomeSource;
import com.bespectacled.modernbeta.biome.PreBetaBiomeSource;
import com.bespectacled.modernbeta.gen.settings.OldGeneratorSettings;
import com.bespectacled.modernbeta.noise.*;
import com.bespectacled.modernbeta.structure.BetaStructure;
import com.bespectacled.modernbeta.util.BlockStates;
import com.bespectacled.modernbeta.util.GenUtil;
import com.bespectacled.modernbeta.util.MutableBiomeArray;

//private final BetaGeneratorSettings settings;

public class InfdevOldChunkGenerator extends NoiseChunkGenerator implements IOldChunkGenerator {

    public static final Codec<InfdevOldChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    Codec.LONG.fieldOf("seed").stable().forGetter(generator -> generator.worldSeed),
                    OldGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings))
            .apply(instance, instance.stable(InfdevOldChunkGenerator::new)));

    private final OldGeneratorSettings settings;
    private final OldBiomeSource biomeSource;
    private final long seed;
    
    private boolean generateInfdevPyramid = true;
    private boolean generateInfdevWall = true;

    private final PerlinOctaveNoise noiseOctavesA;
    private final PerlinOctaveNoise noiseOctavesB;
    private final PerlinOctaveNoise noiseOctavesC;
    private final PerlinOctaveNoise noiseOctavesD;
    private final PerlinOctaveNoise noiseOctavesE;
    private final PerlinOctaveNoise noiseOctavesF;

    // Block Y-height cache, from Beta+
    private static final Map<BlockPos, Integer> GROUND_CACHE_Y = new HashMap<>();
    private static final Block BLOCK_ARR[][][] = new Block[16][128][16];
    private static final Block STRUCT_ARR[][][] = new Block[16][128][16];
    
    private static final Mutable POS = new Mutable();
    
    private static final Random RAND = new Random();
    private static final ChunkRandom FEATURE_RAND = new ChunkRandom();
    
    private static final ObjectList<StructurePiece> STRUCTURE_LIST = new ObjectArrayList<StructurePiece>(10);
    private static final ObjectList<JigsawJunction> JIGSAW_LIST = new ObjectArrayList<JigsawJunction>(32);

    public InfdevOldChunkGenerator(BiomeSource biomes, long seed, OldGeneratorSettings infdevOldSettings) {
        super(biomes, seed, () -> infdevOldSettings.wrapped);
        this.settings = infdevOldSettings;
        this.biomeSource = (OldBiomeSource) biomes;
        this.seed = seed;
        
        RAND.setSeed(seed);
        
        // Noise Generators
        noiseOctavesA = new PerlinOctaveNoise(RAND, 16, false);
        noiseOctavesB = new PerlinOctaveNoise(RAND, 16, false);
        noiseOctavesC = new PerlinOctaveNoise(RAND, 8, false);
        noiseOctavesD = new PerlinOctaveNoise(RAND, 4, false);
        noiseOctavesE = new PerlinOctaveNoise(RAND, 4, false);
        noiseOctavesF = new PerlinOctaveNoise(RAND, 5, false);
        
        new PerlinOctaveNoise(RAND, 3, false);
        new PerlinOctaveNoise(RAND, 3, false);
        new PerlinOctaveNoise(RAND, 3, false);
        
        if (settings.settings.contains("generateInfdevPyramid")) 
            this.generateInfdevPyramid = settings.settings.getBoolean("generateInfdevPyramid");
        if (settings.settings.contains("generateInfdevWall")) 
            this.generateInfdevWall = settings.settings.getBoolean("generateInfdevWall");
    }

    public static void register() {
        Registry.register(Registry.CHUNK_GENERATOR, new Identifier(ModernBeta.ID, "infdev_old"), CODEC);
        //ModernBeta.LOGGER.log(Level.INFO, "Registered Infdev chunk generator.");
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return InfdevOldChunkGenerator.CODEC;
    }

    @Override
    public void populateNoise(WorldAccess worldAccess, StructureAccessor structureAccessor, Chunk chunk) {
        //RAND.setSeed((long) chunk.getPos().x * 341873128712L + (long) chunk.getPos().z * 132897987541L);

        generateTerrain(chunk.getPos().getStartX(), chunk.getPos().getStartZ());  
        setTerrain((ChunkRegion)worldAccess, chunk, structureAccessor);
        
        if (this.biomeSource.isVanilla()) {
            GenUtil.injectOceanBiomes(chunk, this.biomeSource);
        }
    }
        
    @Override
    public void generateFeatures(ChunkRegion chunkRegion, StructureAccessor structureAccessor) {
        GenUtil.generateFeaturesWithOcean(chunkRegion, structureAccessor, this, FEATURE_RAND, this.biomeSource.isVanilla());
    }
    
    @Override
    public void carve(long seed, BiomeAccess biomeAccess, Chunk chunk, GenerationStep.Carver carver) {
        GenUtil.carveWithOcean(this.seed, biomeAccess, chunk, carver, this, biomeSource, FEATURE_RAND, this.getSeaLevel(), this.biomeSource.isVanilla());
    }
    
    @Override
    public void setStructureStarts(
        DynamicRegistryManager dynamicRegistryManager, 
        StructureAccessor structureAccessor,   
        Chunk chunk, 
        StructureManager structureManager, 
        long seed
    ) {
        GenUtil.setStructureStartsWithOcean(dynamicRegistryManager, structureAccessor, chunk, structureManager, seed, this, this.biomeSource, this.biomeSource.isVanilla());
    }
    
    private void setTerrain(ChunkRegion region, Chunk chunk, StructureAccessor structureAccessor) {
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        
        Heightmap heightmapOCEAN = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmapSURFACE = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        
        GenUtil.collectStructures(chunk, structureAccessor, STRUCTURE_LIST, JIGSAW_LIST);
        
        ObjectListIterator<StructurePiece> structureListIterator = (ObjectListIterator<StructurePiece>) STRUCTURE_LIST.iterator();
        ObjectListIterator<JigsawJunction> jigsawListIterator = (ObjectListIterator<JigsawJunction>) JIGSAW_LIST.iterator();
        
        Biome biome;
        
        for (int x = 0; x < 16; ++x) {
            int absX = x + startX;
            
            for (int z = 0; z < 16; ++z) {
                int absZ = z + startZ;
                
                for (int y = 0; y < 128; ++y) {
                    Block blockToSet = BLOCK_ARR[x][y][z];
                    
                    // Second check is a hack to stop weird chunk borders generating from surface blocks for ocean biomes
                    // being picked up and replacing topsoil blocks, somehow before biome reassignment.  Why?!
                    if (this.biomeSource.isVanilla() && GenUtil.getSolidHeight(chunk, absX, absZ) >= 60) {
                        biome = region.getBiome(POS.set(absX, 0, absZ));
                        
                        if (blockToSet == Blocks.GRASS_BLOCK) 
                            blockToSet = biome.getGenerationSettings().getSurfaceConfig().getTopMaterial().getBlock();
                        if (blockToSet == Blocks.DIRT) 
                            blockToSet = biome.getGenerationSettings().getSurfaceConfig().getUnderMaterial().getBlock();
                    }
                    
                    blockToSet = GenUtil.getStructBlock(
                            structureListIterator, 
                            jigsawListIterator, 
                            STRUCTURE_LIST.size(), 
                            JIGSAW_LIST.size(), 
                            absX, y, absZ, blockToSet);

                    if (blockToSet != Blocks.AIR) {
                        chunk.setBlockState(POS.set(x, y, z), BlockStates.getBlockState(blockToSet), false);
                    }
                    
                    heightmapOCEAN.trackUpdate(x, y, z, BlockStates.getBlockState(blockToSet));
                    heightmapSURFACE.trackUpdate(x, y, z, BlockStates.getBlockState(blockToSet));
                        
                }
            }
        }
        
    }
  
    private void generateTerrain(int startX, int startZ) {
        
        for (int relX = 0; relX < 16; ++relX) {
            int x = startX + relX;
            int rX = x / 1024;
            
            for (int relZ = 0; relZ < 16; ++relZ) {    
                int z = startZ + relZ;
                int rZ = z / 1024;
                
                float noiseA = (float)(
                    this.noiseOctavesA.sampleOldInfdevOctaves(x / 0.03125f, 0.0, z / 0.03125f) - 
                    this.noiseOctavesB.sampleOldInfdevOctaves(x / 0.015625f, 0.0, z / 0.015625f)) / 512.0f / 4.0f;
                float noiseB = (float)this.noiseOctavesE.sampleInfdevOctaves(x / 4.0f, z / 4.0f);
                float noiseC = (float)this.noiseOctavesF.sampleInfdevOctaves(x / 8.0f, z / 8.0f) / 8.0f;
                
                noiseB = noiseB > 0.0f ? 
                    ((float)(this.noiseOctavesC.sampleInfdevOctaves(x * 0.25714284f * 2.0f, z * 0.25714284f * 2.0f) * noiseC / 4.0)) :
                    ((float)(this.noiseOctavesD.sampleInfdevOctaves(x * 0.25714284f, z * 0.25714284f) * noiseC));
                    
                int heightVal = (int)(noiseA + 64.0f + noiseB);
                if ((float)this.noiseOctavesE.sampleInfdevOctaves(x, z) < 0.0f) {
                    heightVal = heightVal / 2 << 1;
                    if ((float)this.noiseOctavesE.sampleInfdevOctaves(x / 5, z / 5) < 0.0f) {
                        ++heightVal;
                    }
                }
                
                for (int y = 0; y < 128; ++y) {
                    Block blockToSet = Blocks.AIR;
                    
                    if (this.generateInfdevWall && (x == 0 || z == 0) && y <= heightVal + 2) {
                        blockToSet = Blocks.OBSIDIAN;
                    }
                    else if (!this.biomeSource.isVanilla() && y == heightVal + 1 && heightVal >= 64 && Math.random() < 0.02) {
                        blockToSet = Blocks.DANDELION;
                    }
                    else if (y == heightVal && heightVal >= 64) {
                        blockToSet = Blocks.GRASS_BLOCK;
                    }
                    else if (y <= heightVal - 2) {
                        blockToSet = Blocks.STONE;
                    }
                    else if (y <= heightVal) {
                        blockToSet = Blocks.DIRT;
                    }
                    else if (y <= 64) {
                        blockToSet = Blocks.WATER;
                    }
                    
                    if (this.generateInfdevPyramid) {
                        RAND.setSeed(rX + rZ * 13871);
                        int bX = (rX << 10) + 128 + RAND.nextInt(512);
                        int bZ = (rZ << 10) + 128 + RAND.nextInt(512);
                        
                        bX = x - bX;
                        bZ = z - bZ;
                        
                        if (bX < 0) bX = -bX;
                        if (bZ < 0) bZ = -bZ;
                        
                        if (bZ > bX) bX = bZ;
                        if ((bX = 127 - bX) == 255) bX = 1;
                        if (bX < heightVal) bX = heightVal;
                        
                        if (y <= bX && (blockToSet == Blocks.AIR || blockToSet == Blocks.WATER))
                            blockToSet = Blocks.BRICKS;     
                    }
                    
                    if (y <= 0 + RAND.nextInt(5)) {
                        blockToSet = Blocks.BEDROCK;
                    }
                              
                    
                    BLOCK_ARR[relX][y][relZ] = blockToSet;
                }
                    
            }
        }
    }
    
    @Override
    public void buildSurface(ChunkRegion chunkRegion, Chunk chunk) {
    }
    
    @Override
    public int getHeight(int x, int z, Heightmap.Type type) {
        BlockPos structPos = new BlockPos(x, 0, z);
        int height = this.getWorldHeight() - 1;
        
        if (GROUND_CACHE_Y.get(structPos) == null) {            
            height = this.sampleHeightMap(x, z);
            
            GROUND_CACHE_Y.put(structPos, height);
        } 
        
        height = GROUND_CACHE_Y.get(structPos);
        
        // Not ideal
        if (type == Heightmap.Type.WORLD_SURFACE_WG && height < this.getSeaLevel())
            height = this.getSeaLevel() + 1;
         
        return height;
    }
    
    private int sampleHeightMap(int sampleX, int sampleZ) {
        
        int startX = (sampleX >> 4) << 4;
        int startZ = (sampleZ >> 4) << 4;
        
        int x = startX + Math.abs(sampleX) % 16;
        int z = startZ + Math.abs(sampleZ) % 16;
        
        float noiseA = (float)(
            this.noiseOctavesA.sampleOldInfdevOctaves(x / 0.03125f, 0.0, z / 0.03125f) - 
            this.noiseOctavesB.sampleOldInfdevOctaves(x / 0.015625f, 0.0, z / 0.015625f)) / 512.0f / 4.0f;
        float noiseB = (float)this.noiseOctavesE.sampleInfdevOctaves(x / 4.0f, z / 4.0f);
        float noiseC = (float)this.noiseOctavesF.sampleInfdevOctaves(x / 8.0f, z / 8.0f) / 8.0f;
        
        noiseB = noiseB > 0.0f ? 
            ((float)(this.noiseOctavesC.sampleInfdevOctaves(x * 0.25714284f * 2.0f, z * 0.25714284f * 2.0f) * noiseC / 4.0)) :
            ((float)(this.noiseOctavesD.sampleInfdevOctaves(x * 0.25714284f, z * 0.25714284f) * noiseC));
            
        int heightVal = (int)(noiseA + 64.0f + noiseB);
        if ((float)this.noiseOctavesE.sampleInfdevOctaves(x, z) < 0.0f) {
            heightVal = heightVal / 2 << 1;
            if ((float)this.noiseOctavesE.sampleInfdevOctaves(x / 5, z / 5) < 0.0f) {
                ++heightVal;
            }
        }
        
        return heightVal;
    }
    
    @Override
    public List<SpawnSettings.SpawnEntry> getEntitySpawnList(Biome biome, StructureAccessor structureAccessor, SpawnGroup spawnGroup, BlockPos blockPos) {
        if (spawnGroup == SpawnGroup.MONSTER) {
            if (structureAccessor.getStructureAt(blockPos, false, BetaStructure.OCEAN_SHRINE_STRUCTURE).hasChildren()) {
                return BetaStructure.OCEAN_SHRINE_STRUCTURE.getMonsterSpawns();
            }
        }

        return super.getEntitySpawnList(biome, structureAccessor, spawnGroup, blockPos);
    }
    
    @Override
    public int getWorldHeight() {
        return 128;
    }

    @Override
    public int getSeaLevel() {
        return this.settings.wrapped.getSeaLevel();
    }

    @Override
    public ChunkGenerator withSeed(long seed) {
        return new InfdevOldChunkGenerator(this.biomeSource.withSeed(seed), seed, this.settings);
    }
    
}
