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
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

        // Tránh vòng lặp vô hạn khi chính plugin thu cần ăn cá
        if (isWinningClick.getOrDefault(playerId, false)) {
            return; 
        }

        // 1. Rút ngắn thời gian chờ cá cắn câu
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

        // 2. Cá vừa cắn câu -> Khởi chạy minigame
        if (event.getState() == PlayerFishEvent.State.BITE) {
            FishHook hook = event.getHook();
            if (hook == null || hook.getLocation() == null) return;
            
            // Hiệu ứng môi trường
            FishingEnvironmentManager.FishingArea area = FishingEnvironmentManager.getArea(hook.getLocation());
            hook.getWorld().spawnParticle(FishingEnvironmentManager.getParticle(area), hook.getLocation(), 20, 0.1, 0.1, 0.1, 0.01);
            hook.getWorld().playSound(hook.getLocation(), FishingEnvironmentManager.getBiteSound(area), 1.0F, 1.0F);

            if (!localActiveGames.containsKey(playerId)) {
                FishingGameSession gameSession;
                
                int chanceBar = cfg.getChanceBar();
                int chanceRedGreen = cfg.getChanceRedGreen();
                int chanceKeep = cfg.getChanceKeep();
                int chanceClick = cfg.getChanceClick();
                int totalChance = chanceBar + chanceRedGreen + chanceKeep + chanceClick;

                if (totalChance > 0) {
                    int rand = ThreadLocalRandom.current().nextInt(totalChance);
                    if (rand < chanceBar) {
                        gameSession = new BarMinigame(player, hook);
                    } else if (rand < chanceBar + chanceRedGreen) {
                        gameSession = new RedGreenMinigame(player, hook);
                    } else if (rand < chanceBar + chanceRedGreen + chanceKeep) {
                        gameSession = new KeepFishMinigame(player, hook);
                    } else {
                        gameSession = new ClickSpeedMinigame(player, hook);
                    }
                } else {
                    gameSession = new BarMinigame(player, hook);
                }
                
                localActiveGames.put(playerId, gameSession);
                
                plugin.getFishingManager().registerSession(player, new FishingManager.FishingGameSessionBridge() {
                    @Override
                    public void onInput() {
                        FishingGameSession session = localActiveGames.get(playerId);
                        if (session != null) session.onInput();
                    }
                    @Override
                    public void forceStop() {
                        FishingGameSession session = localActiveGames.get(playerId);
                        if (session != null) session.stop();
                    }
                });

                gameSession.start();
            }
            return;
        }

        // 3. Chặn tương tác thu cần mặc định khi minigame đang chạy
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || event.getState() == PlayerFishEvent.State.REEL_IN) {
            if (localActiveGames.containsKey(playerId)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction().name().contains("RIGHT")) {
            if (player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD || 
                player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD) {
                
                if (localActiveGames.containsKey(player.getUniqueId())) {
                    event.setCancelled(true); 
                    plugin.getFishingManager().handlePlayerClick(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        FishingGameSession session = localActiveGames.remove(playerId);
        if (session != null) {
            session.stop();
        }
        plugin.getFishingManager().removeSession(player);
        isWinningClick.remove(playerId);
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
            
            // Tạo phần thưởng cá rơi ra (Nên tích hợp gọi từ câu cấu hình phần thưởng của plugin)
            Item caughtEntity = hook.getWorld().dropItem(hook.getLocation(), new ItemStack(Material.COD));
            
            // Chạy logic nhét thẳng vào túi đồ người chơi bảo mật không bị loot mất
            if (caughtEntity.isValid()) {
                ItemStack finalFishItem = caughtEntity.getItemStack();
                HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(finalFishItem);
                for (ItemStack drop : leftOver.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                caughtEntity.remove();
            }
            hook.remove(); 
            isWinningClick.remove(playerId); // Dọn dẹp sạch sau khi xử lý xong hook
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

    // =========================================================================
    // 📊 MINIGAME 1: THANH BAR CHẠY MŨI TÊN
    // =========================================================================
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
            if (cursorPos >= barLength || cursorPos <= 0) {
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

    // =========================================================================
    // 🚥 MINIGAME 2: ĐÈN ĐỎ ĐÈN XANH (Đã sửa lỗi Threading Task rác)
    // =========================================================================
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

    // =========================================================================
    // 🐟 MINIGAME 3: GIỮ CÁ TRONG Ô (Tối ưu mượt mà)
    // =========================================================================
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

            // Thanh vợt tự động tụt dần theo trọng lực
            if (ticksCount % 3 == 0 && basketStart > 0) {
                basketStart--;
            }

            // Cá AI nhảy ngẫu nhiên vị trí đích
            if (ticksCount % fishMoveTicks == 0) {
                int moveRange = ThreadLocalRandom.current().nextInt(-4, 5);
                targetFishPos = Math.max(0, Math.min(barLength - 1, targetFishPos + moveRange));
            }

            // Di chuyển cá tiệm cận mượt tới vị trí đích
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
            // Click đẩy thanh basket giật lên trên đầu chuỗi
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

    // =========================================================================
    // ⚡ MINIGAME 4: ĐUA TỐC ĐỘ CLICK
    // =========================================================================
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
            renderBar.append(ChatColor.YELLOW).append(ChatColor.BOLD).append("GÌ CẦN: ")
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