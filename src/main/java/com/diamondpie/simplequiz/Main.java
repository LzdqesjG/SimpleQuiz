package com.diamondpie.simplequiz;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public final class Main extends JavaPlugin {
    private Economy econ = null;
    private QuizManager quizManager;
    private DataManager dataManager;
    private static final Logger log = Logger.getLogger("SimpleQuiz");

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Setup Vault
        if (!setupEconomy()) {
            getLogger().warning("未找到 Vault 或经济插件，经济奖励功能将被禁用");
        }

        // Initialize Manager
        this.quizManager = new QuizManager(this);
        this.dataManager = new DataManager(this);

        // Register Commands
        Objects.requireNonNull(getCommand("simplequiz")).setExecutor(new CommandSimpleQuiz(this));
        Objects.requireNonNull(getCommand("simplequiz")).setTabCompleter(new CommandSimpleQuiz(this));

        // Register Events
        getServer().getPluginManager().registerEvents(new QuizListener(this), this);

        // Start Loop
        this.quizManager.scheduleNextRound();

        getLogger().info("SimpleQuiz 已加载");
    }

    @Override
    public void onDisable() {
        if (this.quizManager != null) {
            this.quizManager.stopQuiz(null, true); // Cleanup
        }
        getLogger().info("SimpleQuiz 已卸载");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public Economy getEconomy() {
        return econ;
    }

    public QuizManager getQuizManager() {
        return quizManager;
    }
    public DataManager getDataManager() {
        return dataManager;
    }
}