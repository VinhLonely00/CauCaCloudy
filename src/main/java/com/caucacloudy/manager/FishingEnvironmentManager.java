package com.caucacloudy.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Biome;

public class FishingEnvironmentManager {

    public enum FishingArea {
        OCEAN,
        SWAMP,
        NORMAL
    }

    public static FishingArea getArea(Location loc) {
        Biome biome = loc.getBlock().getBiome();
        if (biome.name().contains("OCEAN") || biome.name().contains("BEACH")) {
            return FishingArea.OCEAN;
        } else if (biome.name().contains("SWAMP")) {
            return FishingArea.SWAMP;
        }
        return FishingArea.NORMAL;
    }

    public static Particle getParticle(FishingArea area) {
        switch (area) {
            case OCEAN:
                // Sử dụng WATER_WAKE (bọt sóng nước) luôn có sẵn trên các bản Spigot cổ/mới
                return Particle.WATER_WAKE;
            case SWAMP:
                return Particle.SPELL_WITCH;
            default:
                return Particle.WATER_SPLASH;
        }
    }

    public static Sound getBiteSound(FishingArea area) {
        switch (area) {
            case OCEAN:
                return Sound.ENTITY_PLAYER_SPLASH;
            case SWAMP:
                return Sound.BLOCK_MUD_BREAK;
            default:
                return Sound.ENTITY_FISH_SWIM;
        }
    }
}