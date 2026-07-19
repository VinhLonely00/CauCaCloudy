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
        // Dùng toString() thay vì name() để tương thích tốt với hệ thống Registry mới của Spigot/Paper
        String biomeName = biome.toString().toUpperCase();
        
        if (biomeName.contains("OCEAN") || biomeName.contains("BEACH")) {
            return FishingArea.OCEAN;
        } else if (biomeName.contains("SWAMP")) {
            return FishingArea.SWAMP;
        }
        return FishingArea.NORMAL;
    }

    public static Particle getParticle(FishingArea area) {
        // Sử dụng các hạt chuẩn cơ bản của 1.20.5+ và 1.21 không bao giờ bị đổi tên
        switch (area) {
            case OCEAN:
                return Particle.FISHING; // Hạt bong bóng cần câu, phiên bản nào cũng có
            case SWAMP:
                return Particle.WITCH; // Hạt phù thủy huyền bí cho đầm lầy
            default:
                return Particle.SPLASH; // Hạt nước bắn thông thường
        }
    }

    public static Sound getBiteSound(FishingArea area) {
        switch (area) {
            case OCEAN:
                return Sound.ENTITY_PLAYER_SPLASH;
            case SWAMP:
                try {
                    // Tránh lỗi nếu server chạy phiên bản không có âm thanh bùn
                    return Sound.valueOf("BLOCK_MUD_BREAK");
                } catch (IllegalArgumentException e) {
                    return Sound.ENTITY_FISH_SWIM;
                }
            default:
                return Sound.ENTITY_FISH_SWIM;
        }
    }
}