package com.diamondpie.simplequiz;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CommandSimpleQuiz implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public CommandSimpleQuiz(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission("simplequiz.use")) {
                sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
                return true;
            }
            sendInfo(sender);
            return true;
        }

        if (!sender.hasPermission("simplequiz.admin")) {
            sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                plugin.getQuizManager().reload();
                Bukkit.broadcast(Component.text("[问答挑战] 配置已重载", NamedTextColor.GREEN));
                validatePrizeConfig(sender);
                break;

            case "start":
                if (plugin.getQuizManager().isQuizRunning()) {
                    sender.sendMessage(Component.text("[问答挑战] 当前已有问答正在进行中！", NamedTextColor.RED));
                    return true;
                }

                String type = null;
                Integer customDuration = null;

                if (args.length > 1) {
                    String inputType = args[1].toLowerCase();
                    if (inputType.equals("text") || inputType.equals("math")) {
                        type = inputType;
                    } else if (!inputType.equals("random")) {
                        // 如果输入不是 text/math/random，可以给个提示
                        sender.sendMessage(Component.text("未知类型: " + args[1] + " (可选: text, math, random)", NamedTextColor.RED));
                        return true;
                    }
                }

                if (args.length > 2) {
                    try {
                        customDuration = Integer.parseInt(args[2]);
                        if (customDuration <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("持续时间必须是一个正整数！", NamedTextColor.RED));
                        return true;
                    }
                }

                plugin.getQuizManager().startQuiz(type, customDuration);
                String durationMsg = (customDuration != null) ? " (持续 " + customDuration + " 秒)" : "";
                sender.sendMessage(Component.text("已强制开始 " + (type == null ? "随机" : type) + " 问答" + durationMsg, NamedTextColor.GREEN));
                break;

            case "encodehand":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    sender.sendMessage(Component.text("手中没有物品", NamedTextColor.RED));
                    return true;
                }
                String base64 = ItemUtil.itemStackToBase64(hand, plugin.getLogger());
                if (base64 != null) {
                    Component msg = Component.text("物品编码成功，点击复制: ", NamedTextColor.GREEN)
                            .append(Component.text("[点击复制]", NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.copyToClipboard(base64)));
                    sender.sendMessage(msg);
                } else {
                    sender.sendMessage(Component.text("编码失败", NamedTextColor.RED));
                }
                break;

            case "ban":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("用法: /simplequiz ban <玩家名>", NamedTextColor.RED));
                    return true;
                }

                String inputName = args[1];
                OfflinePlayer target;

                // 优先匹配在线玩家，提高效率
                Player onlinePlayer = Bukkit.getPlayer(inputName);
                if (onlinePlayer != null) {
                    target = onlinePlayer;
                } else {
                    target = Bukkit.getOfflinePlayer(inputName);
                }

                // 如果该玩家既不在线，也从未玩过，则视为无效玩家
                if (!target.isOnline() && !target.hasPlayedBefore()) {
                    sender.sendMessage(Component.text("错误: 找不到玩家 " + inputName + " 的游玩记录。", NamedTextColor.RED));
                    return true;
                }

                UUID uuid = target.getUniqueId();
                String realName = (target.getName() != null) ? target.getName() : inputName;

                if (!plugin.getDataManager().isBanned(uuid)) {
                    plugin.getDataManager().banPlayer(uuid);
                    sender.sendMessage(Component.text("已封禁玩家 " + realName, NamedTextColor.GOLD));
                    Bukkit.broadcast(Component.text("[问答挑战] 玩家 " + realName + " 因作弊被永久禁止答题！", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("该玩家已被封禁", NamedTextColor.YELLOW));
                }
                break;

            case "unban":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("用法: /simplequiz unban <玩家名>", NamedTextColor.RED));
                    return true;
                }

                UUID unbanId;
                String unbanName;

                // 先尝试在线玩家
                Player onlineUnban = Bukkit.getPlayer(args[1]);
                if (onlineUnban != null) {
                    unbanId = onlineUnban.getUniqueId();
                    unbanName = onlineUnban.getName();
                } else {
                    // 尝试离线玩家
                    OfflinePlayer offlineUnban = Bukkit.getOfflinePlayer(args[1]);
                    if (offlineUnban.hasPlayedBefore() || offlineUnban.getName() != null) {
                        unbanId = offlineUnban.getUniqueId();
                        unbanName = offlineUnban.getName() != null ? offlineUnban.getName() : args[1];
                    } else {
                        sender.sendMessage(Component.text("找不到玩家: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                }

                if (plugin.getDataManager().isBanned(unbanId)) {
                    plugin.getDataManager().unbanPlayer(unbanId);
                    sender.sendMessage(Component.text("已解封玩家 " + unbanName, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("该玩家未被封禁", NamedTextColor.YELLOW));
                }
                break;

            default:
                sender.sendMessage(Component.text("未知子命令", NamedTextColor.RED));
        }
        return true;
    }

    private void validatePrizeConfig(CommandSender sender) {
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("prize.item.enable")) {
            String base64 = config.getString("prize.item.base64");
            if (base64 != null && !base64.isEmpty()) {
                ItemStack item = ItemUtil.itemStackFromBase64(base64, plugin.getLogger());
                if (item == null) {
                    sender.sendMessage(Component.text("警告: 配置中的物品 Base64 无效或解码失败！", NamedTextColor.RED));
                } else {
                    Component itemName = item.displayName(); // Paper API 获取显示名称
                    sender.sendMessage(Component.text("恭喜，你的 Base64 配置物品已生效: ", NamedTextColor.GREEN).append(itemName));
                }
            }
        }
    }

    private void sendInfo(CommandSender sender) {
        Component title = Component.text("--- SimpleQuiz Info ---", NamedTextColor.GOLD);
        sender.sendMessage(title);
        sender.sendMessage(Component.text("版本: " + plugin.getPluginMeta().getVersion(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("开发者: ", NamedTextColor.BLUE).append(Component.text(String.join(", ", plugin.getPluginMeta().getAuthors()), NamedTextColor.LIGHT_PURPLE)));

        long diff = plugin.getQuizManager().getNextRoundTime() - System.currentTimeMillis();
        Component statusComponent;
        if (plugin.getQuizManager().isQuizRunning()) {
            statusComponent = Component.text("进行中", NamedTextColor.GREEN);
        } else if (diff <= 0) {
            statusComponent = Component.text("仍未开始", NamedTextColor.RED);
        } else {
            statusComponent = Component.text(diff / 1000, NamedTextColor.LIGHT_PURPLE).append(Component.text("秒后", NamedTextColor.AQUA));
        }
        sender.sendMessage(Component.text("下一轮: ", NamedTextColor.AQUA).append(statusComponent));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (sender.hasPermission("simplequiz.admin")) {
                list.add("reload");
                list.add("start");
                list.add("encodehand");
                list.add("ban");
                list.add("unban");
            }
            return filter(list, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start") && sender.hasPermission("simplequiz.admin")) {
                List<String> list = new ArrayList<>();
                list.add("text");
                list.add("math");
                list.add("random");
                return filter(list, args[1]);
            }
            if (args[0].equalsIgnoreCase("ban") && sender.hasPermission("simplequiz.admin")) {
                return null; // 返回 null 默认补全在线玩家名
            }
            if (args[0].equalsIgnoreCase("unban") && sender.hasPermission("simplequiz.admin")) {
                List<String> bannedNames = new ArrayList<>();
                for (String uuidStr : plugin.getDataManager().getBannedUUIDs()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                    if (op.getName() != null) bannedNames.add(op.getName());
                }
                return filter(bannedNames, args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start") && sender.hasPermission("simplequiz.admin")) {
            return Collections.singletonList("[Duration...]");
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> input, String check) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            if (s.toLowerCase().startsWith(check.toLowerCase())) result.add(s);
        }
        return result;
    }
}