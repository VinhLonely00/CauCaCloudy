package com.caucacloudy.command;

import com.caucacloudy.CauCaCloudy;
import com.caucacloudy.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class FishingCommand implements CommandExecutor {

    private final CauCaCloudy plugin;

    public FishingCommand(CauCaCloudy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        
        // Kiểm tra quyền hạn admin
        if (!sender.hasPermission("caucacloudy.admin")) {
            sender.sendMessage(cfg.getPrefix() + cfg.getNoPermission());
            return true;
        }

        // Xử lý lệnh reload cấu hình
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            
            // 1. Buộc Bukkit đọc lại file config.yml đang nằm ở đĩa cứng vào RAM
            plugin.reloadConfig();
            
            // 2. Buộc ConfigManager quét lại toàn bộ dữ liệu trong RAM vừa nạp để cập nhật các biến (%)
            plugin.getConfigManager().loadConfigValues();
            
            // 3. Nếu bạn vẫn muốn giữ hàm dọn dẹp hệ thống cũ của bạn thì gọi ở đây:
            plugin.reloadPlugin(); 
            
            // Cập nhật lại đối tượng cfg cục bộ để in ra tin nhắn chính xác nhất nếu bạn có đổi màu prefix
            cfg = plugin.getConfigManager();
            sender.sendMessage(cfg.getPrefix() + cfg.getReloadSuccess());
            return true;
        }

        // Hướng dẫn sử dụng nếu gõ sai cú pháp
        sender.sendMessage(cfg.getPrefix() + ChatColor.YELLOW + "Sử dụng: /" + label + " reload");
        return true;
    }
}