package com.caucacloudy.manager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FishingEnvironmentManager {

    public enum FishingArea { WATER, LAVA, VOID }

    /**
     * Xác định môi trường (Lava/Void/Water) dựa trên vị trí phao.
     */
    public static FishingArea getArea(Location loc) {
        Block b = loc.getBlock();
        if (b.getType() == Material.LAVA) return FishingArea.LAVA;
        if (isVoid(loc)) return FishingArea.VOID;
        return FishingArea.WATER;
    }

    // Kiểm tra xem bên dưới có block nào trong 10 block không
    private static boolean isVoid(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        int y = loc.getBlockY() - 1;
        for (int i = 0; i < 10; i++) {
            if (!world.getBlockAt(loc.getBlockX(), y - i, loc.getBlockZ()).isEmpty()) return false;
        }
        return true;
    }

    public static Particle getParticle(FishingArea area) {
        if (area == FishingArea.LAVA) return Particle.LAVA;
        if (area == FishingArea.VOID) return Particle.WITCH;
        return Particle.WATER_BUBBLE;
    }

    public static Sound getBiteSound(FishingArea area) {
        if (area == FishingArea.LAVA) return Sound.BLOCK_FIRE_EXTINGUISH;
        if (area == FishingArea.VOID) return Sound.ENTITY_ENDERMAN_TELEPORT;
        return Sound.BLOCK_LAVA_POP;
    }

    /**
     * Tạo hiệu ứng splash (ItemDisplay)
     */
    public static void spawnSplashEffect(Location loc, int customModelData, float size, double offset_y) {
        Location spawnLoc = loc.clone();
        spawnLoc.setY(spawnLoc.getBlockY() + 1 + offset_y);

        ItemStack itemStack = new ItemStack(Material.PAPER);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) return;
        
        itemMeta.setCustomModelData(customModelData);
        itemStack.setItemMeta(itemMeta);

        ItemDisplay itemDisplay = (ItemDisplay) loc.getWorld().spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
        itemDisplay.setItemStack(itemStack);
        
        itemDisplay.setTransformation(new Transformation(
            new Vector3f(0.0F, 0.0F, 0.0F), 
            new Quaternionf(), 
            new Vector3f(size, size, size), 
            new Quaternionf()
        ));

        // Hủy sau 20 tick (1 giây)
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("CauCaCloudy"), itemDisplay::remove, 20L);
    }
}