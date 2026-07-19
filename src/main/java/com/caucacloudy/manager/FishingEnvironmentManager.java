package com.caucacloudy.manager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

public class FishingEnvironmentManager {

    public enum FishingArea {
        OCEAN,
        SWAMP,
        LAVA,       // Môi trường dung nham (Nether / Overworld)
        THE_END,    // Môi trường thế giới kết thúc (Bay lơ lửng)
        NORMAL
    }

    /**
     * Nhận diện chính xác môi trường dựa trên vị trí của phao câu
     */
    public static FishingArea getArea(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return FishingArea.NORMAL;
        }

        // 1. Kiểm tra nếu phao câu ngập trong Dung Nham
        if (loc.getBlock().getType() == Material.LAVA) {
            return FishingArea.LAVA;
        }

        // 2. Kiểm tra dựa trên tính chất của thế giới The End
        if (loc.getWorld().getEnvironment() == World.Environment.THE_END) {
            return FishingArea.THE_END;
        }

        // 3. Quét thêm Biome để nhận diện các vùng End phụ hoặc khoảng không đặc biệt
        Biome biome = loc.getBlock().getBiome();
        String biomeName = biome.toString().toUpperCase();
        
        if (biomeName.contains("THE_END") || biomeName.contains("END_")) {
            return FishingArea.THE_END;
        }
        
        // 4. Các môi trường nước Overworld thông thường
        if (biomeName.contains("OCEAN") || biomeName.contains("BEACH")) {
            return FishingArea.OCEAN;
        } else if (biomeName.contains("SWAMP")) {
            return FishingArea.SWAMP;
        }
        
        return FishingArea.NORMAL;
    }

    /**
     * Trả về hiệu ứng hạt chuẩn tương ứng cho từng môi trường (Tương thích 1.21+)
     */
    public static Particle getParticle(FishingArea area) {
        switch (area) {
            case LAVA:
                return Particle.LAVA; // Bong bóng lửa dung nham nổ tung
            case THE_END:
                return Particle.PORTAL; // Hạt dịch chuyển tím của ngọc Ender/Enderman
            case OCEAN:
                return Particle.FISHING; // Hạt bong bóng nước cần câu gốc
            case SWAMP:
                return Particle.WITCH; // Hạt phép thuật phù thủy đầm lầy màu tím đậm
            default:
                return Particle.SPLASH; // Hạt nước bắn mặc định
        }
    }

    /**
     * Trả về âm thanh đặc trưng khi cá đớp phao câu (Bite Event)
     */
    public static Sound getBiteSound(FishingArea area) {
        switch (area) {
            case LAVA:
                try {
                    return Sound.valueOf("BLOCK_LAVA_EXTINGUISH"); // Tiếng xèo xèo tắt lửa ngầu lòi
                } catch (IllegalArgumentException e) {
                    return Sound.BLOCK_FIRE_EXTINGUISH;
                }
            case THE_END:
                try {
                    return Sound.valueOf("ENTITY_ENDERMAN_TELEPORT"); // Tiếng vụt biến mất huyền bí
                } catch (IllegalArgumentException e) {
                    return Sound.ENTITY_ENDER_DRAGON_FLAP;
                }
            case OCEAN:
                return Sound.ENTITY_PLAYER_SPLASH;
            case SWAMP:
                try {
                    return Sound.valueOf("BLOCK_MUD_BREAK");
                } catch (IllegalArgumentException e) {
                    return Sound.ENTITY_FISH_SWIM;
                }
            default:
                return Sound.ENTITY_FISH_SWIM;
        }
    }

    /**
     * Hàm bổ trợ (Học tập từ file ví dụ): Kiểm tra xem bên dưới phao câu có block nào không.
     * Rất hữu ích khi làm logic câu cá lơ lửng trên không trung (Void) ở The End.
     */
    public static boolean hasBlockBelow(Location loc, int depth) {
        Location clone = loc.clone();
        for (int i = 0; i < depth; i++) {
            if (!clone.add(0, -1, 0).getBlock().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}