package com.example.bot;

import com.example.bot.bot.BotManager;
import com.example.bot.command.BotCommand;
import com.example.bot.config.BotConfig;
import com.example.bot.storage.BotStorage;

import org.bukkit.plugin.java.JavaPlugin;

public class BotPlugin extends JavaPlugin {

    private static BotPlugin instance;

    private BotConfig config;
    private BotStorage storage;
    private BotManager manager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = new BotConfig();
        config.load(getConfig());

        storage = new BotStorage(getDataFolder(), getLogger());
        manager = new BotManager(this, config, storage);
        getServer().getPluginManager().registerEvents(new BotListener(this, manager, config), this);

        BotCommand executor = new BotCommand(manager, config);
        var botCmd = getCommand("bot");
        if (botCmd == null) {
            getLogger().severe("Command 'bot' is not defined in plugin.yml - the plugin cannot function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        botCmd.setExecutor(executor);
        botCmd.setTabCompleter(executor);

        manager.start();

        getLogger().info("Bot plugin enabled. " + manager.all().size() + " persistent bot(s) restored.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        instance = null;
    }

    public static BotPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Bot plugin is not enabled.");
        }
        return instance;
    }

    public BotConfig getBotConfig() {
        return config;
    }

    public BotManager getBotManager() {
        return manager;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        config.load(getConfig());
        if (manager != null) manager.reloadScheduling();
    }
}
