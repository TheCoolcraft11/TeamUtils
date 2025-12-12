package de.thecoolcraft11.teamUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");


        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }


        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public boolean isTeamChatEnabled(String teamName) {
        String path = "teams." + teamName + ".chat-enabled";
        if (config.contains(path)) {
            return config.getBoolean(path);
        }

        return config.getBoolean("settings.default-chat-enabled", true);
    }

    public boolean isTeamBackpackDisabled(String teamName) {
        String path = "teams." + teamName + ".backpack-enabled";
        if (config.contains(path)) {
            return !config.getBoolean(path);
        }

        return !config.getBoolean("settings.default-backpack-enabled", true);
    }

    public int getTeamBackpackSize(String teamName) {
        String path = "teams." + teamName + ".backpack-size";
        if (config.contains(path)) {
            int size = config.getInt(path);

            return normalizeInventorySize(size);
        }

        return normalizeInventorySize(config.getInt("settings.default-backpack-size", 54));
    }

    public void setTeamChatEnabled(String teamName, boolean enabled) {
        config.set("teams." + teamName + ".chat-enabled", enabled);
        saveConfig();
    }

    public void setTeamBackpackEnabled(String teamName, boolean enabled) {
        config.set("teams." + teamName + ".backpack-enabled", enabled);
        saveConfig();
    }

    public void setTeamBackpackSize(String teamName, int size) {
        int normalizedSize = normalizeInventorySize(size);
        config.set("teams." + teamName + ".backpack-size", normalizedSize);
        saveConfig();
    }


    public boolean isGlobalChatEnabled() {
        return config.getBoolean("settings.global-enabled", true);
    }

    public void setGlobalEnabled(boolean enabled) {
        config.set("settings.global-enabled", enabled);
        saveConfig();
    }

    public Set<String> getConfiguredTeams() {
        if (config.contains("teams")) {
            return Objects.requireNonNull(config.getConfigurationSection("teams")).getKeys(false);
        }
        return new HashSet<>();
    }

    private int normalizeInventorySize(int size) {
        if (size <= 9) return 9;
        if (size <= 18) return 18;
        if (size <= 27) return 27;
        if (size <= 36) return 36;
        if (size <= 45) return 45;
        return 54;
    }

    public FileConfiguration getConfig() {
        return config;
    }
}

