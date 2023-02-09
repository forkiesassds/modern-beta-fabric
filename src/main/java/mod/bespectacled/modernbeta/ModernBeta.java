package mod.bespectacled.modernbeta;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import mod.bespectacled.modernbeta.client.color.BlockColors;
import mod.bespectacled.modernbeta.command.DebugProviderSettingsCommand;
import mod.bespectacled.modernbeta.compat.Compat;
import mod.bespectacled.modernbeta.config.ModernBetaConfig;
import mod.bespectacled.modernbeta.config.ModernBetaConfigBiome;
import mod.bespectacled.modernbeta.config.ModernBetaConfigCaveBiome;
import mod.bespectacled.modernbeta.config.ModernBetaConfigChunk;
import mod.bespectacled.modernbeta.config.ModernBetaConfigRendering;
import mod.bespectacled.modernbeta.world.ModernBetaWorldInitializer;
import mod.bespectacled.modernbeta.world.biome.ModernBetaBiomeSource;
import mod.bespectacled.modernbeta.world.carver.ModernBetaCarvers;
import mod.bespectacled.modernbeta.world.chunk.ModernBetaChunkGenerator;
import mod.bespectacled.modernbeta.world.feature.ModernBetaFeatures;
import mod.bespectacled.modernbeta.world.feature.placement.ModernBetaPlacementTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class ModernBeta implements ModInitializer {
    public static final String MOD_ID = "modern_beta";
    public static final String MOD_NAME = "Modern Beta";
    
    public static final boolean CLIENT_ENV = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    public static final boolean DEV_ENV = FabricLoader.getInstance().isDevelopmentEnvironment();

    public static final ModernBetaConfig CONFIG = AutoConfig.register(
        ModernBetaConfig.class, 
        PartitioningSerializer.wrap(GsonConfigSerializer::new)
    ).getConfig();
    
    public static final ModernBetaConfigChunk CHUNK_CONFIG = CONFIG.chunkConfig;
    public static final ModernBetaConfigBiome BIOME_CONFIG = CONFIG.biomeConfig;
    public static final ModernBetaConfigCaveBiome CAVE_BIOME_CONFIG = CONFIG.caveBiomeConfig;
    public static final ModernBetaConfigRendering RENDER_CONFIG = CONFIG.renderingConfig;

    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    
    public static Identifier createId(String name) {
        return new Identifier(MOD_ID, name);
    }
    
    public static void log(Level level, String message) {
        LOGGER.log(level, "[" + MOD_NAME + "] {}", message);
    }
    
    @Override
    public void onInitialize() {
        log(Level.INFO, "Initializing Modern Beta...");
        
        // Register mod stuff
        ModernBetaPlacementTypes.register();
        ModernBetaFeatures.register();
        ModernBetaCarvers.register();
        
        ModernBetaBiomeSource.register();
        ModernBetaChunkGenerator.register();
        
        // Set up mod compatibility
        Compat.setupCompat();
        
        // Register default providers
        ModernBetaBuiltInProviders.registerChunkProviders();
        ModernBetaBuiltInProviders.registerBiomeProviders();
        ModernBetaBuiltInProviders.registerCaveBiomeProviders();
        ModernBetaBuiltInProviders.registerSurfaceConfigs();
        ModernBetaBuiltInProviders.registerNoisePostProcessors();
        ModernBetaBuiltInProviders.registerBlockSources();

        if (CLIENT_ENV) {
            // Override default biome grass/foliage colors
            BlockColors.register();
        }
        
        if (DEV_ENV) {
            DebugProviderSettingsCommand.register();
        }
        
        // Initializes chunk and biome providers at server start-up.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ModernBetaWorldInitializer.init(server));
    }
}