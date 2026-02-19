package com.diamondpie.simplequiz;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class QuizManager {

    private final Main plugin;
    private boolean isRunning = false;
    private boolean isPausedByPlayerCount = false;

    private final Set<String> currentAnswers = new HashSet<>();
    private String currentQuestionText = "";
    private String currentJudgeHint = "";

    private BukkitTask timeoutTask;
    private BukkitTask nextRoundTask;
    private BukkitTask bossBarTask;

    private long nextRoundTime = 0;
    private BossBar activeBossBar;

    public QuizManager(Main plugin) {
        this.plugin = plugin;
        checkPlayerCount();
    }

    public void reload() {
        stopQuiz(null, true);
        if (nextRoundTask != null) nextRoundTask.cancel();
        plugin.reloadConfig();

        checkPlayerCount();
        if (!isPausedByPlayerCount) {
            scheduleNextRound();
        }
    }

    public void checkPlayerCount() {
        int min = plugin.getConfig().getInt("min-player", 5);
        int current = Bukkit.getOnlinePlayers().size();
        boolean shouldPause = current < min;

        if (shouldPause && !isPausedByPlayerCount) {
            // 状态改变：人数足够 -> 人数不足
            isPausedByPlayerCount = true;
            stopQuiz(null, true); // 停止当前问题
            if (nextRoundTask != null) nextRoundTask.cancel(); // 取消下一轮调度
            nextRoundTime = 0;

            if (plugin.getConfig().getBoolean("broadcast-status")) {
                Bukkit.broadcast(Component.text("[问答挑战] 在线人数不足 " + min + " 人，问答暂时停止", NamedTextColor.RED));
            }

        } else if (!shouldPause && isPausedByPlayerCount) {
            // 状态改变：人数不足 -> 人数足够
            isPausedByPlayerCount = false;
            scheduleNextRound(); // 恢复调度

            if (plugin.getConfig().getBoolean("broadcast-status")) {
                Bukkit.broadcast(Component.text("[问答挑战] 在线人数已达标，问答即将恢复！", NamedTextColor.GREEN));
            }
        }
    }

    public boolean isQuizRunning() {
        return isRunning;
    }

    public String getJudgeHint() { return currentJudgeHint; }

    public boolean checkAnswer(Player player, String message) {
        if (!isRunning) return false;

        String cleanMsg = message.trim();
        for (String ans : currentAnswers) {
            if (ans.equalsIgnoreCase(cleanMsg)) {
                handleCorrectAnswer(player);
                return true;
            }
        }
        return false;
    }

    public long getNextRoundTime() {
        return nextRoundTime;
    }

    public void scheduleNextRound() {
        if (isPausedByPlayerCount) return;

        if (nextRoundTask != null && !nextRoundTask.isCancelled()) {
            nextRoundTask.cancel();
        }
        long interval = plugin.getConfig().getLong("interval", 300);
        nextRoundTime = System.currentTimeMillis() + (interval * 1000);

        nextRoundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> startQuiz(null, null), getAdaptedTicks(interval));
    }

    public void startQuiz(String forcedType, Integer customDuration) {
        if (isRunning) return;

        // Determine Type
        String type = forcedType;
        if (type == null) {
            double mathChance = plugin.getConfig().getDouble("math.chance", 0.5);
            type = (ThreadLocalRandom.current().nextDouble() < mathChance) ? "math" : "text";
        }

        prepareQuestion(type);

        isRunning = true;
        // Broadcast Question
        Component prefix = Component.text("[问答挑战] ", NamedTextColor.GOLD);
        Component question = Component.text(currentQuestionText, NamedTextColor.YELLOW);
        Bukkit.broadcast(prefix.append(question));

        Bukkit.broadcast(Component.text("请直接在公屏输入答案" + currentJudgeHint, NamedTextColor.GRAY));
        broadcastSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);

        // Schedule Timeout
        long duration = (customDuration != null) ? customDuration : plugin.getConfig().getLong("duration", 30);

        if (plugin.getConfig().getBoolean("show-bossbar")) {
            activeBossBar = BossBar.bossBar(
                    Component.text("问答挑战倒计时：" + duration + "秒", NamedTextColor.YELLOW),
                    1.0f,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.PROGRESS
            );
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(activeBossBar);
            }

            // Bossbar refresh task
            final long startTime = System.currentTimeMillis();

            bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!isRunning || activeBossBar == null) return;

                long elapsedMillis = System.currentTimeMillis() - startTime;
                float progress = 1.0f - ((float) elapsedMillis / (duration * 1000f));
                long secondsLeft = Math.max(0, duration - (elapsedMillis / 1000) - 1);

                progress = Math.max(0, progress);

                activeBossBar.progress(progress);
                activeBossBar.name(Component.text("问答挑战倒计时：" + secondsLeft + "秒", NamedTextColor.YELLOW));

                if (secondsLeft <= 5) {
                    broadcastSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f + 0.1f*(5-secondsLeft));
                }
            }, getAdaptedTicks(1), getAdaptedTicks(1));
        }
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Component timeOutPrefix = Component.text("[问答挑战] ", NamedTextColor.GOLD);
            Component msg = Component.text("本轮时间已到，无人回答正确", NamedTextColor.RED);
            Component ansMsg = Component.text("正确答案是: " + String.join(" 或 ", currentAnswers), NamedTextColor.AQUA);
            Bukkit.broadcast(timeOutPrefix.append(msg));
            Bukkit.broadcast(timeOutPrefix.append(ansMsg));
            stopQuiz(null, false);
        }, getAdaptedTicks(duration));
    }

    private void prepareQuestion(String type) {
        currentAnswers.clear();
        currentJudgeHint = ""; // Reset judge hint
        FileConfiguration config = plugin.getConfig();

        if ("math".equalsIgnoreCase(type)) {
            generateMathQuestion(config);
        } else {
            generateTextQuestion(config);
        }
    }

    private void generateTextQuestion(FileConfiguration config) {
        List<Map<?, ?>> questions = config.getMapList("fill");
        if (questions.isEmpty()) {
            currentQuestionText = "配置文件中没有填空题";
            currentAnswers.add("admin");
            return;
        }

        Map<?, ?> q = questions.get(ThreadLocalRandom.current().nextInt(questions.size()));
        currentQuestionText = (String) q.get("quiz");

        Map<?, ?> answerSection = (Map<?, ?>) q.get("answer");
        String ansType = (String) answerSection.get("type");

        if ("judge".equalsIgnoreCase(ansType)) {
            boolean boolVal = (Boolean) answerSection.get("value");
            String keyYes = config.getString("judge.ansYes", "是");
            String keyNo = config.getString("judge.ansNo", "否");
            String key = boolVal ? keyYes : keyNo;
            currentAnswers.add(key);
            currentJudgeHint = " (" + keyYes + "/" + keyNo + ")";
        } else {
            Object valueObj = answerSection.get("value");
            if (valueObj instanceof List<?> rawList) {
                for (Object obj : rawList) {
                    if (obj instanceof String) {
                        currentAnswers.add((String) obj);
                    } else {
                        currentAnswers.add(String.valueOf(obj));
                    }
                }
            }
        }
    }

    private void generateMathQuestion(FileConfiguration config) {
        int minOp = config.getInt("math.operator.min", 1);
        int maxOp = config.getInt("math.operator.max", 3);
        int countOp = ThreadLocalRandom.current().nextInt(minOp, maxOp + 1);

        int minNum = config.getInt("math.number.min", 0);
        int maxNum = config.getInt("math.number.max", 99);

        // Easy multiplier logic configuration
        boolean easyMultEnabled = config.getBoolean("math.easy-multiplier.enable", false);
        int easyMin = config.getInt("math.easy-multiplier.min", 1);
        int easyMax = config.getInt("math.easy-multiplier.max", 9);

        List<Integer> numbers = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        String[] ops = {"+", "-", "*"};

        // First generate operators
        for (int i = 0; i < countOp; i++) {
            operators.add(ops[ThreadLocalRandom.current().nextInt(ops.length)]);
        }

        // Generate first number
        numbers.add(ThreadLocalRandom.current().nextInt(minNum, maxNum + 1));
        // Generate the rest of numbers
        for (int i = 0; i < countOp; i++) {
            String op = operators.get(i);
            int num;
            // If easy multiplier is enabled, force generate a small number
            if (easyMultEnabled && op.equals("*")) {
                num = ThreadLocalRandom.current().nextInt(easyMin, easyMax + 1);
            } else {
                num = ThreadLocalRandom.current().nextInt(minNum, maxNum + 1);
            }
            numbers.add(num);
        }

        // Build String
        StringBuilder sb = new StringBuilder();
        sb.append(numbers.getFirst());
        for (int i = 0; i < operators.size(); i++) {
            sb.append(" ").append(operators.get(i)).append(" ").append(numbers.get(i + 1));
        }
        currentQuestionText = "请计算: " + sb;

        // Calculate Result
        currentAnswers.add(String.valueOf(solveMath(numbers, operators)));
    }

    private int solveMath(List<Integer> numbers, List<String> operators) {
        // Create mutable copies
        List<Integer> nums = new ArrayList<>(numbers);
        List<String> ops = new ArrayList<>(operators);

        // Process * first
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).equals("*")) {
                int val = nums.get(i) * nums.get(i + 1);
                nums.set(i, val);
                nums.remove(i + 1);
                ops.remove(i);
                i--;
            }
        }

        // Process + and -
        int result = nums.getFirst();
        for (int i = 0; i < ops.size(); i++) {
            String op = ops.get(i);
            if (op.equals("+")) {
                result += nums.get(i + 1);
            } else {
                result -= nums.get(i + 1);
            }
        }
        return result;
    }

    private void handleCorrectAnswer(Player winner) {
        if (plugin.getDataManager().isBanned(winner.getUniqueId())) {
            winner.sendMessage(Component.text("我们不接受作弊者的答案", NamedTextColor.RED));
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Delay for 1 tick so the message falls below player's answer
            Component prefix = Component.text("[问答挑战] ", NamedTextColor.GOLD);
            Component msg = Component.text(winner.getName() + " 回答正确！", NamedTextColor.GREEN);
            Bukkit.broadcast(prefix.append(msg));
        }, 1L);

        // 切换回主线程发放奖励
        Bukkit.getScheduler().runTask(plugin, () -> {
            giveRewards(winner);
            stopQuiz(winner, false);
        });
    }

    private void giveRewards(Player p) {
        FileConfiguration config = plugin.getConfig();

        // Item Reward
        if (config.getBoolean("prize.item.enable")) {
            String base64 = config.getString("prize.item.base64");
            ItemStack item = null;

            if (base64 != null && !base64.isEmpty()) {
                item = ItemUtil.itemStackFromBase64(base64, plugin.getLogger());
            }

            if (item == null) {
                String id = config.getString("prize.item.id", "minecraft:diamond");
                Material mat = Material.matchMaterial(id);
                if (mat != null) item = new ItemStack(mat);
            }

            if (item != null) {
                int min = config.getInt("prize.item.amount.min", 1);
                int max = config.getInt("prize.item.amount.max", 1);
                int amt = ThreadLocalRandom.current().nextInt(min, max + 1);
                item.setAmount(amt);
                var leftovers = p.getInventory().addItem(item);
                if (leftovers.isEmpty()) {
                    p.sendMessage(Component.text("获得物品奖励 x" + amt, NamedTextColor.GREEN));
                } else {
                    leftovers.values().forEach(leftItem -> p.getWorld().dropItemNaturally(p.getLocation(), leftItem));
                    p.sendMessage(Component.text("你的物品栏已满！多余的奖励已掉落在你脚下！", NamedTextColor.GOLD));
                }
            }
        }

        // Economy Reward
        if (config.getBoolean("prize.economy.enable") && plugin.getEconomy() != null) {
            int min = config.getInt("prize.economy.amount.min", 0);
            int max = config.getInt("prize.economy.amount.max", 0);
            double amount = ThreadLocalRandom.current().nextInt(min, max + 1);
            plugin.getEconomy().depositPlayer(p, amount);
            p.sendMessage(Component.text("获得金币奖励 " + amount, NamedTextColor.GREEN));
        }
    }

    public void stopQuiz(Player winner, Boolean mute) {
        isRunning = false;
        currentAnswers.clear();
        if (timeoutTask != null) timeoutTask.cancel();

        // Clear Bossbar
        if (bossBarTask != null) {
            bossBarTask.cancel();
            bossBarTask = null;
        }
        if (activeBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(activeBossBar);
            }
            activeBossBar = null;
        }

        // Broadcast sound
        if (!mute) {
            if (winner != null) {
                broadcastSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            } else {
                broadcastSound(Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
            }
        }

        if (!isPausedByPlayerCount) {
            scheduleNextRound();
        }
    }

    public void broadcastSound(Sound sound, float volume, float pitch) {
        if (plugin.getConfig().getBoolean("mute", false)) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private long getAdaptedTicks(long seconds) {
        // TODO: Refactor main logic to adapt tasks to reality time, not game ticks
        double tps = Bukkit.getTPS()[0];
        // Prevent tps from overflow or underflow
        double effectiveTps = Math.max(1.0, Math.min(20.0, tps));
        return (long) (seconds * effectiveTps);
    }
}