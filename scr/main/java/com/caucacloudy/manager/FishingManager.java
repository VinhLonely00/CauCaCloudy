package com.caucacloudy.manager;

import com.caucacloudy.CauCaCloudy;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FishingManager {

    private final CauCaCloudy plugin;
    // Map này lưu session game hiện tại của người chơi để chuyển tiếp tương tác Click
    private final Map<UUID, FishingGameSessionBridge> activeSessions = new HashMap<>();

    public FishingManager(CauCaCloudy plugin) {
        this.plugin = plugin;
    }

    /**
     * Đăng ký session minigame đang chạy của người chơi vào Manager quản lý
     */
    public void registerSession(Player player, FishingGameSessionBridge session) {
        activeSessions.put(player.getUniqueId(), session);
    }

    /**
     * Chuyển tiếp hành động Click chuột phải từ PlayerInteractEvent vào đúng Minigame đang chơi
     */
    public void handlePlayerClick(Player player) {
        FishingGameSessionBridge session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.executeInput();
        }
    }

    /**
     * Xóa session của một người chơi cụ thể (khi thoát game hoặc kết thúc trò chơi)
     */
    public void removeSession(Player player) {
        FishingGameSessionBridge session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.executeStop();
        }
    }

    /**
     * Dừng toàn bộ trò chơi đang chạy trên cụm Server (Dùng khi Reload hoặc tắt Plugin)
     */
    public void clearAllSessions() {
        for (FishingGameSessionBridge session : activeSessions.values()) {
            session.executeStop();
        }
        activeSessions.clear();
    }

    /**
     * Interface bắc cầu kết nối dữ liệu từ Manager sang các Session trong Listener
     */
    public interface FishingGameSessionBridge {
        void executeInput();
        void executeStop();
    }
}