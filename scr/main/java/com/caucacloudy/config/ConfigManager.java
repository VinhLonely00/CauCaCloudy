package com.caucacloudy.config;

import com.caucacloudy.CauCaCloudy;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

@SuppressWarnings("deprecation")
public class ConfigManager {

    private final CauCaCloudy plugin;

    // Cấu hình chung
    private boolean minigameEnabled;
    private int minWaitTime;
    private int maxWaitTime;

    // Tỉ lệ xuất hiện (%) minigame
    private int chanceBar;
    private int chanceRedGreen;
    private int chanceKeepFish;
    private int chanceClickSpeed;

    // Cấu hình Minigame 1 (Thanh Bar)
    private int barLength;
    private int greenZoneSize;
    private int gameSpeedTicks;

    // Cấu hình Minigame 2 (Đèn Đỏ Đèn Xanh)
    private int redLightTicks;
    private int greenLightTicks;
    private int minBlankTicks;
    private int maxBlankTicks;
    private int maxTurns;
    private String redIcon;
    private String greenIcon;
    private String timeoutIcon;

    // Cấu hình Minigame 3 (Giữ Cá Trong Ô)
    private int keepBarLength;
    private int basketSize;
    private int gravityTicks;
    private int jumpStrength;
    private int fishMoveTicks;
    private int winProgressTarget;
    private int progressGain;
    private int progressLoss;

    // Cấu hình Minigame 4 (Đua Tốc Độ Click)
    private double clickTimeLimitSeconds;
    private int clickMinClicks;
    private int clickMaxClicks;

    // Tin nhắn hệ thống
    private String prefix;
    private String reloadSuccess;
    private String noPermission;

    public ConfigManager(CauCaCloudy plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    public void loadConfigValues() {
        FileConfiguration config = plugin.getConfig(); // Đã sửa thành hàm chuẩn của Bukkit

        // 1. Nạp cấu hình chung
        this.minigameEnabled = config.getBoolean("fishing-minigame.enabled", true);
        this.minWaitTime = config.getInt("fishing-minigame.min-wait-time", 2);
        this.maxWaitTime = config.getInt("fishing-minigame.max-wait-time", 5);

        // 1.5 Nạp tỉ lệ xuất hiện minigame
        this.chanceBar = config.getInt("fishing-minigame.chance.bar-game", 25);
        this.chanceRedGreen = config.getInt("fishing-minigame.chance.red-green-game", 25);
        this.chanceKeepFish = config.getInt("fishing-minigame.chance.keep-fish-game", 25);
        this.chanceClickSpeed = config.getInt("fishing-minigame.chance.click-speed-game", 25);

        // 2. Nạp cấu hình Minigame 1
        this.barLength = config.getInt("fishing-minigame.bar-length", 30);
        this.greenZoneSize = config.getInt("fishing-minigame.green-zone-size", 6);
        this.gameSpeedTicks = config.getInt("fishing-minigame.game-speed-ticks", 2);

        // 3. Nạp cấu hình Minigame 2
        this.redLightTicks = config.getInt("fishing-minigame.red-green-game.red-light-ticks", 6);
        this.greenLightTicks = config.getInt("fishing-minigame.red-green-game.green-light-ticks", 10);
        this.minBlankTicks = config.getInt("fishing-minigame.red-green-game.min-blank-ticks", 16);
        this.maxBlankTicks = config.getInt("fishing-minigame.red-green-game.max-blank-ticks", 30);
        this.maxTurns = config.getInt("fishing-minigame.red-green-game.max-turns", 5);
        this.redIcon = config.getString("fishing-minigame.red-green-game.icons.red", "🔴");
        this.greenIcon = config.getString("fishing-minigame.red-green-game.icons.green", "🟢");
        this.timeoutIcon = config.getString("fishing-minigame.red-green-game.icons.timeout", "❌");

        // 4. Nạp cấu hình Minigame 3
        this.keepBarLength = config.getInt("fishing-minigame.keep-fish-game.bar-length", 25);
        this.basketSize = config.getInt("fishing-minigame.keep-fish-game.basket-size", 5);
        this.gravityTicks = config.getInt("fishing-minigame.keep-fish-game.gravity-ticks", 2);
        this.jumpStrength = config.getInt("fishing-minigame.keep-fish-game.jump-strength", 3);
        this.fishMoveTicks = config.getInt("fishing-minigame.keep-fish-game.fish-move-ticks", 8);
        this.winProgressTarget = config.getInt("fishing-minigame.keep-fish-game.win-progress-target", 100);
        this.progressGain = config.getInt("fishing-minigame.keep-fish-game.progress-gain", 4);
        this.progressLoss = config.getInt("fishing-minigame.keep-fish-game.progress-loss", 5);

        // 5. Nạp cấu hình Minigame 4
        this.clickTimeLimitSeconds = config.getDouble("fishing-minigame.click-speed-game.time-limit-seconds", 3.6);
        this.clickMinClicks = config.getInt("fishing-minigame.click-speed-game.min-clicks", 18);
        this.clickMaxClicks = config.getInt("fishing-minigame.click-speed-game.max-clicks", 25);

        // 6. Nạp tin nhắn hệ thống
        this.prefix = colorize(config.getString("messages.prefix", "&b[CauCa] &r"));
        this.reloadSuccess = colorize(config.getString("messages.reload-success", "&aĐã tải lại cấu hình thành công!"));
        this.noPermission = colorize(config.getString("messages.no-permission", "&cBạn không có quyền thực hiện lệnh này!"));
    }

    private String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // --- GETTERS CHUNG ---
    public boolean isMinigameEnabled() { return minigameEnabled; }
    public int getMinWaitTime() { return minWaitTime; }
    public int getMaxWaitTime() { return maxWaitTime; }

    // --- GETTERS TỈ LỆ (%) XUẤT HIỆN ---
    public int getChanceBar() { return chanceBar; }
    public int getChanceRedGreen() { return chanceRedGreen; }
    public int getChanceKeepFish() { return chanceKeepFish; }
    public int getChanceClickSpeed() { return chanceClickSpeed; }

    // --- GETTERS MINIGAME 1 ---
    public int getBarLength() { return barLength; }
    public int getGreenZoneSize() { return greenZoneSize; }
    public int getGameSpeedTicks() { return gameSpeedTicks; }

    // --- GETTERS MINIGAME 2 ---
    public int getRedLightTicks() { return redLightTicks; }
    public int getGreenLightTicks() { return greenLightTicks; }
    public int getMinBlankTicks() { return minBlankTicks; }
    public int getMaxBlankTicks() { return maxBlankTicks; }
    public int getMaxTurns() { return maxTurns; }
    public String getRedIcon() { return redIcon; }
    public String getGreenIcon() { return greenIcon; }
    public String getTimeoutIcon() { return timeoutIcon; }

    // --- GETTERS MINIGAME 3 ---
    public int getKeepBarLength() { return keepBarLength; }
    public int getBasketSize() { return basketSize; }
    public int getGravityTicks() { return gravityTicks; }
    public int getJumpStrength() { return jumpStrength; }
    public int getFishMoveTicks() { return fishMoveTicks; }
    public int getWinProgressTarget() { return winProgressTarget; }
    public int getProgressGain() { return progressGain; }
    public int getProgressLoss() { return progressLoss; }

    // --- GETTERS MINIGAME 4 ---
    public double getClickTimeLimitSeconds() { return clickTimeLimitSeconds; }
    public int getClickMinClicks() { return clickMinClicks; }
    public int getClickMaxClicks() { return clickMaxClicks; }

    // --- GETTERS MESSAGES ---
    public String getPrefix() { return prefix; }
    public String getReloadSuccess() { return reloadSuccess; }
    public String getNoPermission() { return noPermission; }
}