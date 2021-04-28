package com.bespectacled.modernbeta.gui.screen.biome;

import java.util.function.Consumer;

import com.bespectacled.modernbeta.ModernBeta;
import com.bespectacled.modernbeta.api.gui.AbstractWorldScreenProvider;
import com.bespectacled.modernbeta.util.NBTUtil;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.DoubleOption;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.DynamicRegistryManager;

public class VanillaBiomeScreenProvider extends Screen {
    private final AbstractWorldScreenProvider parent;
    //private final DynamicRegistryManager registryManager;
    private final NbtCompound biomeProviderSettings;
    private final Consumer<NbtCompound> consumer;
    private final NbtCompound vanillaBiomeSettings;
    
    private int vanillaBiomeSize;
    private int vanillaOceanBiomeSize;
    
    private ButtonListWidget buttonList;
    
    private VanillaBiomeScreenProvider(
        AbstractWorldScreenProvider parent, 
        DynamicRegistryManager registryManager, 
        NbtCompound biomeProviderSettings,
        Consumer<NbtCompound> consumer
    ) {
        super(new TranslatableText("createWorld.customize.vanilla.title"));
        
        this.parent = parent;
        //this.registryManager = registryManager;
        this.biomeProviderSettings = biomeProviderSettings;
        this.consumer = consumer;
        this.vanillaBiomeSettings = new NbtCompound();
        
        this.vanillaBiomeSize = NBTUtil.readInt("vanillaBiomeSize", biomeProviderSettings, ModernBeta.BETA_CONFIG.biome_config.vanillaBiomeSize);
        this.vanillaOceanBiomeSize = NBTUtil.readInt("vanillaOceanBiomeSize", biomeProviderSettings, ModernBeta.BETA_CONFIG.biome_config.vanillaOceanBiomeSize);
    }
    
    public static VanillaBiomeScreenProvider create(AbstractWorldScreenProvider screenProvider) {
        return new VanillaBiomeScreenProvider(
            screenProvider, 
            screenProvider.getRegistryManager(), 
            screenProvider.getBiomeProviderSettings(),
            vanillaBiomeSettings -> screenProvider.setBiomeProviderSettings(vanillaBiomeSettings)
        );
    }
    
    @Override
    protected void init() {
        this.addButton(new ButtonWidget(
            this.width / 2 - 155, this.height - 28, 150, 20, 
            ScreenTexts.DONE, 
            (buttonWidget) -> {
                this.biomeProviderSettings.copyFrom(this.vanillaBiomeSettings);
                this.consumer.accept(this.biomeProviderSettings);
                this.client.openScreen(this.parent);
            }
        ));

        this.addButton(new ButtonWidget(
            this.width / 2 + 5, this.height - 28, 150, 20, 
            ScreenTexts.CANCEL,
            (buttonWidget) -> {
                this.client.openScreen(this.parent);
            }
        ));
        
        this.buttonList = new ButtonListWidget(this.client, this.width, this.height, 32, this.height - 32, 25);
        
        this.buttonList.addSingleOptionEntry(
            new DoubleOption(
                "createWorld.customize.vanilla.vanillaBiomeSizeSlider", 
                1D, 8D, 1F,
                (gameOptions) -> { return (double)this.vanillaBiomeSize; }, // Getter
                (gameOptions, value) -> { // Setter
                    this.vanillaBiomeSize = value.intValue();
                    this.vanillaBiomeSettings.putInt("vanillaBiomeSize", this.vanillaBiomeSize);
                },
                (gameOptions, doubleOptions) -> {
                    return new TranslatableText(
                        "options.generic_value", 
                        new Object[] { 
                            new TranslatableText("createWorld.customize.vanilla.biomeSize"), 
                            Text.of(String.valueOf(this.vanillaBiomeSize)) 
                    });
                }
        ));
        
        this.buttonList.addSingleOptionEntry(
            new DoubleOption(
                "createWorld.customize.indev.vanillaOceanBiomeSizeSlider", 
                1D, 8D, 1F,
                (gameOptions) -> { return (double)this.vanillaOceanBiomeSize; }, // Getter
                (gameOptions, value) -> { // Setter
                    this.vanillaOceanBiomeSize = value.intValue();
                    this.vanillaBiomeSettings.putInt("vanillaOceanBiomeSize", this.vanillaOceanBiomeSize);
                },
                (gameOptions, doubleOptions) -> {
                    return new TranslatableText(
                        "options.generic_value", 
                        new Object[] { 
                            new TranslatableText("createWorld.customize.vanilla.oceanBiomeSize"), 
                            Text.of(String.valueOf(this.vanillaOceanBiomeSize)) 
                    });
                }
        ));
        
        this.children.add(this.buttonList);
    }
    
    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float tickDelta) {
        this.renderBackground(matrixStack);
        
        this.buttonList.render(matrixStack, mouseX, mouseY, tickDelta);
        DrawableHelper.drawCenteredText(matrixStack, this.textRenderer, this.title, this.width / 2, 16, 16777215);
        
        super.render(matrixStack, mouseX, mouseY, tickDelta);
    }
}