package com.bespectacled.modernbeta.gui.provider;

import java.util.function.BiConsumer;
import com.bespectacled.modernbeta.ModernBeta;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.option.CyclingOption;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.registry.DynamicRegistryManager;

public class InfdevOldLevelScreenProvider extends InfLevelScreenProvider {
    private boolean generateInfdevPyramid;
    private boolean generateInfdevWall;
    
    public InfdevOldLevelScreenProvider(
        CreateWorldScreen parent, 
        DynamicRegistryManager registryManager, 
        NbtCompound biomeProviderSettings, 
        NbtCompound chunkProviderSettings, 
        BiConsumer<NbtCompound, NbtCompound> consumer
    ) {
        super(parent, registryManager, biomeProviderSettings, chunkProviderSettings, consumer);
        
        this.generateInfdevPyramid = this.chunkProviderSettings.contains("generateInfdevPyramid") ? 
            this.chunkProviderSettings.getBoolean("generateInfdevPyramid") :    
            ModernBeta.BETA_CONFIG.generationConfig.generateInfdevPyramid;
        
        this.generateInfdevWall = this.chunkProviderSettings.contains("generateInfdevWall") ? 
            this.chunkProviderSettings.getBoolean("generateInfdevWall") :
            ModernBeta.BETA_CONFIG.generationConfig.generateInfdevWall;
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.buttonList.addSingleOptionEntry(
           CyclingOption.create("createWorld.customize.infdev.generateInfdevPyramid", 
               (gameOptions) -> { return generateInfdevPyramid; }, 
               (gameOptions, option, value) -> { // Setter
                   this.generateInfdevPyramid = value;
                   this.chunkProviderSettings.putBoolean("generateInfdevPyramid", this.generateInfdevPyramid);
       }));
       
       this.buttonList.addSingleOptionEntry(
           CyclingOption.create("createWorld.customize.infdev.generateInfdevWall", 
               (gameOptions) -> { return generateInfdevWall; }, 
               (gameOptions, option, value) -> { // Setter
                   this.generateInfdevWall = value;
                   this.chunkProviderSettings.putBoolean("generateInfdevWall", this.generateInfdevWall);
       }));
    }
}