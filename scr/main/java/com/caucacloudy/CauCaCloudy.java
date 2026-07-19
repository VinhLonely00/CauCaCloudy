package com.caucacloudy;

import com.caucacloudy.command.FishingCommand;
import com.caucacloudy.config.ConfigManager;
import com.caucacloudy.listener.FishingListener;
import com.caucacloudy.manager.FishingManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CauCaCloudy extends JavaPlugin {

    private ConfigManager configManager;
    private FishingManager fishingManager;

    @Override
    public void onEnable() {
        // Tạo file config.yml mặc định nếu chưa tồn tại
        saveDefaultConfig();

        // Khởi tạo các trình quản lý core hệ thống
        this.configManager = new ConfigManager(this);
        this.fishingManager = new FishingManager(this);

        // Đăng ký sự kiện lắng nghe (Listener) và lệnh điều hướng (Command)
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        if (getCommand("caucacloudy") != null) {
            getCommand("caucacloudy").setExecutor(new FishingCommand(this));
        }

        getLogger().info("CauCaCloudy da kich hoat thanh cong!");
    }

    @Override
    public void onDisable() {
        // Dọn dẹp sạch sẽ các game đang chạy dở của người chơi khi tắt/reload server
        if (fishingManager != null) {
            fishingManager.clearAllSessions();
        }
        getLogger().info("CauCaCloudy da dung hoat dong!");
    }

    /**
     * Hàm xử lý logic nạp lại toàn bộ dữ liệu cấu hình từ lệnh /ccc reload
     */
    public void reloadPlugin() {
        reloadConfig();
        if (configManager != null) {
            configManager.loadConfigValues();
        }
        if (fishingManager != null) {
            fishingManager.clearAllSessions();
        }
    }

    // --- CÁC HÀM GETTER ĐỂ KẾT NỐI SANG LISTENER VÀ COMMAND ---
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public FishingManager getFishingManager() {
        return fishingManager;
    }
}