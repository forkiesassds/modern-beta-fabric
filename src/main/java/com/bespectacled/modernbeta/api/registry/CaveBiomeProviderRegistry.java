package com.bespectacled.modernbeta.api.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import com.bespectacled.modernbeta.api.AbstractCaveBiomeProvider;

import net.minecraft.nbt.NbtCompound;

public class CaveBiomeProviderRegistry {
    public enum BuiltInCaveBiomeType {
        NONE("none"),
        VANILLA("vanilla");
        
        public final String name;
        
        private BuiltInCaveBiomeType(String name) { this.name = name; }
    }
    
    private static final Map<String, BiFunction<Long, NbtCompound, AbstractCaveBiomeProvider>> REGISTRY = new HashMap<>(); 
    
    public static void register(String name, BiFunction<Long, NbtCompound, AbstractCaveBiomeProvider> biomeProvider) {
        if (REGISTRY.containsKey(name)) 
            throw new IllegalArgumentException("[Modern Beta] Registry already contains cave biome provider named " + name);
        
        REGISTRY.put(name, biomeProvider);
    }
    
    public static BiFunction<Long, NbtCompound, AbstractCaveBiomeProvider> get(String name) {
        if (!REGISTRY.containsKey(name))
            throw new NoSuchElementException("[Modern Beta] Registry does not contain cave biome provider named " + name);
        
        return REGISTRY.get(name);
    }
    
    public static String getCaveBiomeProviderType(NbtCompound settings) {
        if (settings.contains("caveBiomeType")) 
            return settings.getString("caveBiomeType");
        
        throw new NoSuchElementException("[Modern Beta] Settings does not contain caveBiomeType field!");
    }
}
