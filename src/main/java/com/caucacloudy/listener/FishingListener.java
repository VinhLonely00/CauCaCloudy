package com.caucacloudy.listener;

import com.caucacloudy.CauCaCloudy;
import com.caucacloudy.config.ConfigManager;
import com.caucacloudy.manager.FishingManager;
import com.caucacloudy.manager.FishingEnvironmentManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FishingListener implements Listener {

    private final CauCaCloudy plugin;
    private final Random random = new Random();
    
    // Sử dụng Set hoặc Map để đánh dấu người chơi đang trong trạng thái nhận thưởng nhằm tránh lặp đè Event
    private final Map<UUID, Boolean> isWinningClick = new ConcurrentHashMap<>();
    private final Map<UUID, FishingGameSession> localActiveGames = new ConcurrentHashMap<>();

    public FishingListener(CauCaCloudy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ConfigManager cfg = plugin.getConfigManager();
        
        if (!cfg.isMinigameEnabled()) return;

        // Nếu đang trong luồng xử lý phát thưởng, bỏ qua hoàn toàn để tránh xung đột loop
        if (isWinningClick.getOrDefault(playerId, false)) {
            return; 
        }

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            FishHook hook = event.getHook();
            if (hook != null) {
                int minWait = cfg.getMinWaitTime() * 20; 
                int maxWait = cfg.getMaxWaitTime() * 20;
                int randomWait = random.nextInt((maxWait - minWait) + 1) + minWait;
                hook.setWaitTime(randomWait, randomWait);
            }
            return;
        }

        if (event.getState() == PlayerFishEvent.State.BITE) {
            FishHook hook = event.getHook();
            if (hook == null || hook.getLocation() == null) return;
            
            FishingEnvironmentManager.FishingArea area = FishingEnvironmentManager.getArea(hook.getLocation());
            hook.getWorld().spawnParticle(FishingEnvironmentManager.getParticle(area), hook.getLocation(), 20, 0.1, 0.1, 0.1, 0.01);
            hook.getWorld().playSound(hook.getLocation(), FishingEnvironmentManager.getBiteSound(area), 1.0F, 1.0F);

            if (!localActiveGames.containsKey(playerId)) {
                FishingGameSession gameSession;
                int rand = ThreadLocalRandom.current().nextInt(4);

                if (rand == 0) {
                    gameSession = new BarMinigame(player, hook);
                } else if (rand == 1) {
                    gameSession = new RedGreenMinigame(player, hook);
                } else if (rand == 2) {
                    gameSession = new KeepFishMinigame(player, hook);
                } else {
                    gameSession = new ClickSpeedMinigame(player, hook);
                }
                
                localActiveGames.put(playerId, gameSession);
                
                plugin.getFishingManager().registerSession(player, new FishingManager.FishingGameSessionBridge() {
                    @Override
                    public void executeInput() {
                        FishingGameSession session = localActiveGames.get(playerId);
                        if (session != null) session.onInput();
                    }
                    
                    @Override
                    public void executeStop() {
                        FishingGameSession session = localActiveGames.get(playerId);
                        if (session != null) session.stop();
                    }
                });

                gameSession.start();
            }
            return;
        }

        // Chặn người chơi tự thu cần về bằng cách thông thường khi game đang chạy
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || event.getState() == PlayerFishEvent.State.REEL_IN || event.getState() == PlayerFishEvent.State.IN_GROUND) {
            if (localActiveGames.containsKey(playerId)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction().name().contains("RIGHT")) {
            if (player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD || 
                player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD) {
                
                if (localActiveGames.containsKey(player.getUniqueId())) {
                    event.setCancelled(true); 
                    // Gọi sang manager xử lý input tập trung
                    plugin.getFishingManager().handlePlayerClick(player);
                }
            }
        }
    }

    // --- BỘ LỌC NGĂN CHẶN MEMORY LEAK (RÒ RỈ BỘ NHỚ) ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        forceFailSession(event.getPlayer(), "Bạn đã rời trò chơi khi đang câu cá.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        forceFailSession(event.getEntity(), "Bạn đã tử trận khi đang câu cá!");
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        // Nếu người chơi chuyển đổi item cầm trên tay, hủy lượt chơi cũ
        Player player = event.getPlayer();
        if (localActiveGames.containsKey(player.getUniqueId())) {
            forceFailSession(player, "Hủy câu do thay đổi vật phẩm trên tay.");
        }
    }

    @EventHandler
    public void onOpenInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (localActiveGames.containsKey(player.getUniqueId())) {
                forceFailSession(player, "Hủy câu do mở kho đồ.");
            }
        }
    }

    private void forceFailSession(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        FishingGameSession session = localActiveGames.remove(playerId);
        if (session != null) {
            session.stop();
            // Lấy thực thể Hook (nếu có) để xóa bỏ hoàn toàn khỏi thế giới
            plugin.getFishingManager().removeSession(player);
            isWinningClick.remove(playerId);
            player.sendActionBar(ChatColor.RED + "✕ TRÒ CHƠI BỊ HỦY ✕");
        }
    }

    private interface FishingGameSession {
        void start();
        void onInput();
        void stop();
    }

    private void handleReward(Player player, FishHook hook) {
        UUID playerId = player.getUniqueId();
        ConfigManager cfg = plugin.getConfigManager();

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.sendActionBar(ChatColor.BOLD + "" + ChatColor.GREEN + "★ HOÀN HẢO! CÁ ĐÃ LÊN BỜ ★");
        player.sendMessage(cfg.getPrefix() + ChatColor.GREEN + "Bạn đã giật cần chuẩn xác!");

        if (hook != null && hook.isValid()) {
            isWinningClick.put(playerId, true);
            try {
                Item caughtEntity = hook.getWorld().dropItem(hook.getLocation(), new ItemStack(Material.COD));
                if (caughtEntity.isValid()) {
                    ItemStack finalFishItem = caughtEntity.getItemStack();
                    HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(finalFishItem);
                    for (ItemStack drop : leftOver.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    caughtEntity.remove();
                }
                hook.remove(); 
            } finally {
                // Đặt vào khối block finally để đảm bảo dù có lỗi drop item xảy ra, flag vẫn được gỡ bỏ tránh kẹt người chơi
                isWinningClick.remove(playerId);
            }
        }
    }

    private void handleFailure(Player player, FishHook hook, String customMessage) {
        ConfigManager cfg = plugin.getConfigManager();
        
        player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1.0f, 0.6f);
        player.sendActionBar(ChatColor.BOLD + "" + ChatColor.RED + "✕ TRƯỢT MẤT RỒI! CÁ ĐÃ SỔNG ✕");
        player.sendMessage(cfg.getPrefix() + ChatColor.RED + customMessage);
        
        if (hook != null) {
            hook.remove(); 
        }
    }

    private void finishGameSession(Player player) {
        localActiveGames.remove(player.getUniqueId());
        plugin.getFishingManager().removeSession(player);
    }

    // --- CÁC CLASS MINIGAME INNER ĐƯỢC TỐI ƯU HÓA ---

    private class BarMinigame extends BukkitRunnable implements FishingGameSession {
        private final Player player;
        private final FishHook hook;
        private int cursorPos = 0;
        private int direction = 1;
        private final int barLength;
        private final int successStart;
        private final int successEnd;
        private boolean isActive = true;

        public BarMinigame(Player player, FishHook hook) {
            this.player = player;
            this.hook = hook;
            ConfigManager cfg = plugin.getConfigManager();
            this.barLength = cfg.getBarLength();
            int greenZoneSize = cfg.getGreenZoneSize();
            this.successStart = random.nextInt(Math.max(1, barLength - greenZoneSize));
            this.successEnd = successStart + greenZoneSize;
        }

        @Override
        public void start() {
            this.runTaskTimer(plugin, 0L, plugin.getConfigManager().getGameSpeedTicks());
        }

        @Override
        public void run() {
            if (!isActive || player == null || !player.isOnline() || hook == null || !hook.isValid()) {
                cleanup();
                return;
            }

            cursorPos += direction;
            if (cursorPos >= barLength - 1 || cursorPos <= 0) {
                direction *= -1;
            }

            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barLength; i++) {
                if (i == cursorPos) {
                    bar.append(ChatColor.GOLD).append("▼"); 
                } else if (i >= successStart && i <= successEnd) {
                    bar.append(ChatColor.GREEN).append("█"); 
                } else {
                    bar.append(ChatColor.RED).append("█"); 
                }
            }
            player.sendActionBar(bar.toString());
        }

        @Override
        public void onInput() {
            if (!isActive) return;
            cleanup();

            if (cursorPos >= successStart && cursorPos <= successEnd) {
                handleReward(player, hook);
            } else {
                handleFailure(player, hook, "Hụt rồi! Mũi tên không chỉ trúng Vùng Xanh.");
            }
        }

        @Override
        public void stop() {
            isActive = false;
            try { this.cancel(); } catch (IllegalStateException ignored) {}
        }

        private void cleanup() {
            stop();
            finishGameSession(player);
        }
    }

    private class RedGreenMinigame implements FishingGameSession {
        private final Player player;
        private final FishHook hook;
        
        private int currentLightState = 2; 
        private int currentTurn = 1;
        private final int greenLightTurn;
        private boolean isActive = true;
        private BukkitTask currentTask = null;
        private final ConfigManager cfg;

        public RedGreenMinigame(Player player, FishHook hook) {
            this.player = player;
            this.hook = hook;
            this.cfg = plugin.getConfigManager();
            this.greenLightTurn = ThreadLocalRandom.current().nextInt(1, cfg.getMaxTurns() + 1);
        }

        @Override
        public void start() {
            runSignalLoop();
        }

        private void runSignalLoop() {
            if (!isActive || player == null || !player.isOnline() || hook == null || !hook.isValid()) {
                cleanup();
                return;
            }

            if (currentTurn > cfg.getMaxTurns()) {
                cleanup();
                handleFailure(player, hook, "Cá đã ăn hết mồi và bỏ đi vì bạn không phản ứng!");
                player.sendActionBar(ChatColor.BOLD + cfg.getTimeoutIcon());
                return;
            }

            if (currentTurn == greenLightTurn) {
                currentLightState = 1;
                player.sendActionBar(ChatColor.GREEN + cfg.getGreenIcon());

                currentTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isActive) return;
                    currentLightState = 2;
                    player.sendActionBar("");
                    
                    currentTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!isActive) return;
                        currentTurn++;
                        runSignalLoop();
                    }, 20L);
                }, (long) cfg.getGreenLightTicks());

            } else {
                currentLightState = 0;
                player.sendActionBar(ChatColor.RED + cfg.getRedIcon());

                currentTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isActive) return;
                    currentLightState = 2;
                    player.sendActionBar("");
                    
                    int nextDelay = ThreadLocalRandom.current().nextInt(cfg.getMinBlankTicks(), cfg.getMaxBlankTicks() + 1);
                    currentTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!isActive) return;
                        currentTurn++;
                        runSignalLoop();
                    }, (long) nextDelay);
                }, (long) cfg.getRedLightTicks());
            }
        }

        @Override
        public void onInput() {
            if (!isActive) return;
            cleanup();

            if (currentLightState == 1) {
                handleReward(player, hook);
            } else {
                handleFailure(player, hook, "Hụt rồi! Cá chỉ mới tỉa mồi, bạn đã bấm trúng lúc Đèn Đỏ.");
            }
        }

        @Override
        public void stop() {
            isActive = false;
            if (currentTask != null) {
                currentTask.cancel();
            }
        }

        private void cleanup() {
            stop();
            finishGameSession(player);
        }
    }

    private class KeepFishMinigame extends BukkitRunnable implements FishingGameSession {
        private final Player player;
        private final FishHook hook;

        private final int barLength;
        private final int basketSize;
        private final int jumpStrength;
        private final int fishMoveTicks;
        private final int winTarget;
        private final int gainAmount;
        private final int lossAmount;

        private int basketStart = 0; 
        private int fishPos;         
        private int targetFishPos;   
        private int currentProgress = 50; 
        private int ticksCount = 0;
        private boolean isActive = true;
        private int startDelayTicks = 30; 

        public KeepFishMinigame(Player player, FishHook hook) {
            this.player = player;
            this.hook = hook;

            ConfigManager cfg = plugin.getConfigManager();
            this.barLength = cfg.getKeepBarLength();
            this.basketSize = cfg.getBasketSize();
            this.jumpStrength = cfg.getJumpStrength();
            this.fishMoveTicks = cfg.getFishMoveTicks();
            this.winTarget = cfg.getWinProgressTarget();
            this.gainAmount = cfg.getProgressGain();
            this.lossAmount = cfg.getProgressLoss();

            this.fishPos = this.barLength / 2;
            this.targetFishPos = this.fishPos;
            this.basketStart = Math.max(0, this.fishPos - (this.basketSize / 2));
        }

        @Override
        public void start() {
            this.runTaskTimer(plugin, 0L, (long) plugin.getConfigManager().getGravityTicks());
        }

        @Override
        public void run() {
            if (!isActive || player == null || !player.isOnline() || hook == null || !hook.isValid()) {
                cleanup();
                return;
            }

            int gravityTicks = plugin.getConfigManager().getGravityTicks();
            
            if (startDelayTicks > 0) {
                startDelayTicks -= gravityTicks;
                ticksCount += gravityTicks;
                renderActionBarDisplay();
                return; 
            }

            ticksCount += gravityTicks;

            // Cơ chế trọng lực tự nhiên: Thanh vợt tự động tụt xuống theo thời gian
            if (ticksCount % 3 == 0 && basketStart > 0) {
                basketStart--;
            }

            // Cá bơi ngẫu nhiên sang hai bên vị trí mục tiêu mới
            if (ticksCount % fishMoveTicks == 0) {
                int moveRange = ThreadLocalRandom.current().nextInt(-4, 5);
                targetFishPos = Math.max(0, Math.min(barLength - 1, targetFishPos + moveRange));
            }

            // Di chuyển cá tiệm cận mượt mà về phía mục tiêu bơi
            if (fishPos < targetFishPos) fishPos++;
            else if (fishPos > targetFishPos) fishPos--;

            int basketEnd = basketStart + basketSize;
            boolean isFishInBasket = (fishPos >= basketStart && fishPos < basketEnd);

            if (isFishInBasket) {
                currentProgress += gainAmount; 
            } else {
                currentProgress -= lossAmount; 
            }

            if (currentProgress > winTarget) currentProgress = winTarget;

            if (currentProgress >= winTarget) {
                cleanup();
                handleReward(player, hook);
                return;
            } else if (currentProgress <= 0) {
                cleanup();
                handleFailure(player, hook, "Cá đã vùng vẫy thoát khỏi vợt và làm đứt dây câu!");
                return;
            }

            renderActionBarDisplay();
        }

        private void renderActionBarDisplay() {
            StringBuilder renderBar = new StringBuilder();
            
            if (startDelayTicks > 0) {
                renderBar.append(ChatColor.AQUA).append(ChatColor.BOLD).append("[CHUẨN BỊ] ");
            } else {
                renderBar.append(ChatColor.GOLD).append(ChatColor.BOLD).append("[").append(currentProgress).append("%] ");
            }

            int basketEnd = basketStart + basketSize;
            renderBar.append(ChatColor.DARK_GRAY).append("▕");
            
            for (int i = 0; i < barLength; i++) {
                if (i == fishPos) {
                    if (i >= basketStart && i < basketEnd) {
                        renderBar.append(ChatColor.GREEN).append("🐟"); 
                    } else {
                        renderBar.append(ChatColor.AQUA).append("🐟"); 
                    }
                } else if (i >= basketStart && i < basketEnd) {
                    renderBar.append(ChatColor.GREEN).append("█");
                } else {
                    renderBar.append(ChatColor.GRAY).append("▓"); 
                }
            }
            
            renderBar.append(ChatColor.DARK_GRAY).append("▏");
            player.sendActionBar(renderBar.toString());
        }

        @Override
        public void onInput() {
            if (!isActive) return;
            int maxBasketStart = barLength - basketSize;
            // Mỗi lần click, tăng tọa độ basketStart để đẩy vợt "nhảy" lên
            basketStart = Math.min(maxBasketStart, basketStart + jumpStrength);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
        }

        @Override
        public void stop() {
            isActive = false;
            try { this.cancel(); } catch (IllegalStateException ignored) {}
        }

        private void cleanup() {
            stop();
            finishGameSession(player);
        }
    }

    private class ClickSpeedMinigame extends BukkitRunnable implements FishingGameSession {
        private final Player player;
        private final FishHook hook;

        private int clickCount = 0;          
        private final int requiredClicks;    
        private int timeLimitTicks; 
        private boolean isActive = true;

        public ClickSpeedMinigame(Player player, FishHook hook) {
            this.player = player;
            this.hook = hook;

            ConfigManager cfg = plugin.getConfigManager();
            this.timeLimitTicks = (int) (cfg.getClickTimeLimitSeconds() * 20.0);

            int minClicks = cfg.getClickMinClicks();
            int maxClicks = cfg.getClickMaxClicks();
            if (minClicks > maxClicks) { 
                int temp = minClicks; 
                minClicks = maxClicks; 
                maxClicks = temp; 
            }

            this.requiredClicks = ThreadLocalRandom.current().nextInt(minClicks, maxClicks + 1); 
        }

        @Override
        public void start() {
            this.runTaskTimer(plugin, 0L, 1L);
        }

        @Override
        public void onInput() {
            if (!isActive || timeLimitTicks <= 0) return;

            clickCount++; 
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.4f, 1.6f);

            if (clickCount >= requiredClicks) {
                cleanup();
                handleReward(player, hook);
            }
        }

        @Override
        public void run() {
            if (!isActive || player == null || !player.isOnline() || hook == null || !hook.isValid()) {
                cleanup();
                return;
            }

            timeLimitTicks--; 

            if (timeLimitTicks <= 0) {
                cleanup();
                handleFailure(player, hook, "Cá quẫy quá mạnh, bạn không kịp kéo cần!");
                return;
            }

            renderActionBarDisplay();
        }

        private void renderActionBarDisplay() {
            StringBuilder renderBar = new StringBuilder();
            
            renderBar.append(ChatColor.RED).append(ChatColor.BOLD).append("⏱ ").append(String.format("%.1f", timeLimitTicks / 20.0)).append("s  ");
            renderBar.append(ChatColor.YELLOW).append(ChatColor.BOLD).append("GHÌ CẦN: ")
                     .append(ChatColor.GREEN).append(clickCount).append(ChatColor.WHITE).append("/").append(requiredClicks).append("  ");

            renderBar.append(ChatColor.DARK_GRAY).append("[");
            int totalBars = 10;
            int filledBars = (int) ((double) clickCount / requiredClicks * totalBars);
            for (int i = 0; i < totalBars; i++) {
                if (i < filledBars) {
                    renderBar.append(ChatColor.GREEN).append("█");
                } else {
                    renderBar.append(ChatColor.GRAY).append("░");
                }
            }
            renderBar.append(ChatColor.DARK_GRAY).append("]");
            
            player.sendActionBar(renderBar.toString());
        }

        @Override
        public void stop() {
            isActive = false;
            try { this.cancel(); } catch (IllegalStateException ignored) {}
        }

        private void cleanup() {
            stop();
            finishGameSession(player);
        }
    }
}