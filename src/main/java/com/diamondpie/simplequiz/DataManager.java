package com.diamondpie.simplequiz;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {

    private final Main plugin;
    private File file;
    private FileConfiguration config;
    private final String PATH = "banned-players";

    public DataManager(Main plugin) {
        this.plugin = plugin;
        setup();
    }

    /**
     * 初始化 bans.yml 文件
     */
    public void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        file = new File(plugin.getDataFolder(), "bans.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
                // 初始化一个空的列表结构
                config = YamlConfiguration.loadConfiguration(file);
                config.set(PATH, new ArrayList<String>());
                save();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建 bans.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 bans.yml!");
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 封禁玩家
     */
    public void banPlayer(UUID uuid) {
        List<String> banned = getBannedUUIDs();
        String sUuid = uuid.toString();
        if (!banned.contains(sUuid)) {
            banned.add(sUuid);
            config.set(PATH, banned);
            save();
        }
    }

    /**
     * 解封玩家
     */
    public void unbanPlayer(UUID uuid) {
        List<String> banned = getBannedUUIDs();
        if (banned.remove(uuid.toString())) {
            config.set(PATH, banned);
            save();
        }
    }

    /**
     * 检查玩家是否被封禁
     */
    public boolean isBanned(UUID uuid) {
        return getBannedUUIDs().contains(uuid.toString());
    }

    /**
     * 获取所有封禁 UUID 列表
     */
    public List<String> getBannedUUIDs() {
        return config.getStringList(PATH);
    }
}