package com.hearty.configmanager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;


public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            try {
                plugin.saveResource("config.yml", false); // Save default config from resources
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public Map<String, Pattern> getPreferenceTriggers() {
        Map<String, Pattern> preferenceTriggers = new HashMap<>();
        if (config.contains("preferenceTriggers")) {
            for (String key : config.getConfigurationSection("preferenceTriggers").getKeys(false)) {
                String regex = config.getString("preferenceTriggers." + key);
                preferenceTriggers.put(key, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            }
        }
        return preferenceTriggers;
    }

    public Set<String> getSingleEntryOptions() {
        Set<String> singleEntryOptions = new HashSet<>();
    
        // Load single entry options from the config
        if (config.contains("singleEntryOptions")) {
            singleEntryOptions.addAll(config.getStringList("singleEntryOptions"));
        }

        return singleEntryOptions;
    }

    public Set<String> getForbiddenNicknames() {
        Set<String> nicknames = new HashSet<>();
        if (config.contains("forbiddenNicknames")) {
            nicknames.addAll(config.getStringList("forbiddenNicknames"));
        }
        return nicknames;
    }

    public int getMaxEntriesPerSection() {
        return config.getInt("max_entries_per_section", 10); // Default to 10 if not set
    }

    public int reloadDelayTicks() {
        return config.getInt("reload_Delay_Ticks", 100); // Default to 100 if not set
    }
    public double randomSelectionProbability() {
        return config.getDouble("random_Selection_Probability", 0.1); // Default to 0.1 if not set
    }
    public Map<String, String> getPreferenceSections() {
        Map<String, String> preferenceSections = new HashMap<>();

        // Load preference sections from the config
        if (config.isConfigurationSection("preferenceSections")) {
            for (String key : config.getConfigurationSection("preferenceSections").getKeys(false)) {
                String sectionMessage = config.getString("preferenceSections." + key);
                if (sectionMessage != null) {
                    preferenceSections.put(key, sectionMessage);  // Store the section type as the key and message as the value
                }
            }
        }

        return preferenceSections;
    }
}