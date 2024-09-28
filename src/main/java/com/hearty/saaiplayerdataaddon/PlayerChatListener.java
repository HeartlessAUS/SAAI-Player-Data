package com.hearty.playerchatlistener;

import dev.bluetree242.serverassistantai.api.events.PreMinecraftHandleEvent;
import dev.bluetree242.serverassistantai.api.events.PreDiscordHandleEvent;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hearty.configmanager.ConfigManager;

public class PlayerChatListener implements Listener {
    private final File documentFolder;
    private final JavaPlugin plugin;
    private final Logger logger = Logger.getLogger("SAAI-PlayerData");
    private final Random random = new Random();
    private final ConfigManager configManager;
    private Set<String> forbiddenNicknames;
    private Set<String> singleEntryOptions;
    private int maxEntriesPerSection;
    private int reloadDelayTicks;
    private double randomSelectionProbability;
    private BukkitTask reloadTask = null;
    private boolean debugEnabled;

    // Map of preference types and their corresponding regex patterns
    private final Map<String, Pattern> preferenceTriggers = new HashMap<>();

    public PlayerChatListener(File documentFolder, JavaPlugin plugin, ConfigManager configManager) {
        this.documentFolder = documentFolder;
        this.plugin = plugin;
        this.configManager = configManager;
        initializeConfig();// Initialize preference triggers
    }
    public void reloadPreferences() {
        ClearExisting();
        initializeConfig();  // Initialize preference triggers
    }
    public void ClearExisting() {
        this.preferenceTriggers.clear();
        this.forbiddenNicknames.clear();
        this.singleEntryOptions.clear();
    }
    public void initializeConfig() {
        this.preferenceTriggers.putAll(configManager.getPreferenceTriggers()); // Load triggers
        this.forbiddenNicknames = configManager.getForbiddenNicknames(); // Load forbidden nicknames
        this.singleEntryOptions = configManager.getSingleEntryOptions(); // load single entry options
        this.maxEntriesPerSection = configManager.getMaxEntriesPerSection(); // Load the max entries config
        this.reloadDelayTicks = configManager.getDelayTicks(); // Load the max entries config
        this.randomSelectionProbability = configManager.getRandomSelectionProbability(); // Load the max entries config
        this.debugEnabled = configManager.getDebugMode();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = preprocessMessage(event.getMessage());
        String playerName = event.getPlayer().getName();

        boolean preferenceMatched = processMessageForPreferences(playerName, message);

        if (!preferenceMatched) {
            handleRandomMessageSave(playerName, message);
        }
    }
    @EventHandler
    public void onPreMinecraftHandle(PreMinecraftHandleEvent event) {
        String message = preprocessMessage(event.getMessage());
        String playerName = event.getPlayer().getName();

        boolean preferenceMatched = processMessageForPreferences(playerName, message);

        if (!preferenceMatched) {
            handleRandomMessageSave(playerName, message);
        }
    }

    // New event handling for Discord messages
    /*
    @EventHandler
    public void onPreDiscordHandle(PreDiscordHandleEvent event) {
        String message = preprocessMessage(event.getMessage().toString());
        String discordName = event.getRequester().toString();
        String playerName = configManager.getDiscordToMinecraftNames().getOrDefault(discordName, discordName);       
        boolean preferenceMatched = processMessageForPreferences(playerName, message);

        if (!preferenceMatched) {
            handleRandomMessageSave(playerName, message);
        }
    }*/

    private String preprocessMessage(String message) {
        return message.toLowerCase().replace("'", "").trim();
    }

    private boolean processMessageForPreferences(String playerName, String message) {
        for (Map.Entry<String, Pattern> entry : preferenceTriggers.entrySet()) {
            Matcher matcher = entry.getValue().matcher(message);
            if (matcher.find()) {
                // For blocks, validate the block name
                if (entry.getKey().equals("blocks")){
                    blockCheck(playerName, entry.getKey(), message, matcher, entry);
                } else {
                    storePlayerData(playerName, entry.getKey(), message);
                    return true;
                }
            }
        }
        return false;
    }

    private void blockCheck(String playerName, String preferenceType, String message, Matcher matcher, Map.Entry<String, Pattern> entry) {
        String foundPreference = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
        // For blocks, validate the block name
        if (!isValidBlock(foundPreference)) {
            if (debugEnabled){ logger.log(Level.WARNING, "Invalid block name: " + foundPreference, foundPreference); }
        } else {
            storePlayerData(playerName, entry.getKey(), message);
        }
    }
    private void handleRandomMessageSave(String playerName, String message) {
        if (random.nextDouble() < randomSelectionProbability) {
            storePlayerData(playerName, "Randomly Saved Messages", message);
            if (debugEnabled){ logger.log(Level.INFO, "Randomly added message for player '" + playerName + "': " + message, new Object[]{playerName, message}); }
        } else {
            if (debugEnabled){ logger.log(Level.INFO, "No preference found for message:" + message, message); }
        }
    }


    private void storePlayerData(String playerName, String preferenceType, String message) {
        File playerFile = new File(documentFolder, playerName + ".txt");
        String formattedMessage = message.trim();
        Map<String, StringBuilder> existingPreferences = loadExistingPreferences(playerFile, playerName);
        boolean isNewPreferenceAdded = false;

        // Special handling for nickname and pronouns
        if (singleEntryOptions.contains(preferenceType)) {
            updateSpecialPreference(existingPreferences, preferenceType, formattedMessage, playerName);
        } else {
            removeConflictingPreferences(existingPreferences, preferenceType, formattedMessage);
            isNewPreferenceAdded = appendPreference(existingPreferences, preferenceType, formattedMessage, playerName);
        }

        writePlayerPreferencesToFile(playerFile, playerName, existingPreferences);
        // Only reload if a new preference was added
        if (isNewPreferenceAdded || preferenceType.equals("nickname") || preferenceType.equals("pronouns")) {
           scheduleDelayedReload();  // Call this only if a new preference is added
        }
    }
    private void updateSpecialPreference(Map<String, StringBuilder> preferences, String type, String message, String playerName) {
        String trimmedMessage = message.trim().toLowerCase();  // Ensure it's lowercase for comparison
        String[] words = trimmedMessage.split("\\s+"); // Split by whitespace
        for (String word : words) {
            if (forbiddenNicknames.contains(word)) {
                if (debugEnabled){ logger.log(Level.WARNING, "The Nickname " + word + " is not allowed", new Object[]{word, playerName}); }
                return; // Do not update the nickname if any word is forbidden
            }
        }

        preferences.get(type).setLength(0);  // Clear previous entry
        preferences.get(type).append(trimmedMessage).append("\n");
        if (debugEnabled){ logger.log(Level.INFO, "Updated " + type + " for player " + playerName, new Object[]{type, playerName}); }
    }

    private void removeConflictingPreferences(Map<String, StringBuilder> preferences, String type, String message) {
        for (String prefType : preferenceTriggers.keySet()) {
            if (!prefType.equals(type) && preferences.get(prefType).toString().contains(message)) {
                StringBuilder updatedSection = removeConflictingPreference(preferences.get(prefType), message);
                preferences.get(prefType).setLength(0);  // Clear section
                preferences.get(prefType).append(updatedSection);
            }
        }
    }

    private boolean appendPreference(Map<String, StringBuilder> preferences, String type, String message, String playerName) {
        StringBuilder section = preferences.get(type);
        String[] existingEntries = section.toString().split("\n");

        // Check if the limit is reached
        if (existingEntries.length >= maxEntriesPerSection) {
            if (debugEnabled) {
                logger.log(Level.WARNING, "Max entries reached for " + type + " for player " + playerName, new Object[]{type, playerName});
            }

            // Optional: Remove the oldest entry, but skip those starting with '~'
            StringBuilder updatedSection = new StringBuilder();
            int removedEntries = 0;
        
            for (int i = 0; i < existingEntries.length; i++) {
                String entry = existingEntries[i];
            
                // Skip entries starting with '~'
                if (!entry.startsWith("~")) {
                    removedEntries++;
                    if (removedEntries > 1) {  // Only remove one non-* entry (the oldest)
                        updatedSection.append(entry).append("\n");
                    }
                } else {
                    updatedSection.append(entry).append("\n");
                }
            }

            section.setLength(0); // Clear the section
            section.append(updatedSection); // Add updated entries
        }

        // Now check if the new message can be added
        if (!section.toString().contains(message)) {
            section.append(message).append("\n");
            if (debugEnabled) {
                logger.log(Level.INFO, "Added {0} for player '{1}'", new Object[]{type, playerName});
            }
            return true;  // Return true if a new preference was added
        } else {
            if (debugEnabled) {
                logger.log(Level.INFO, "{0} already exists for player '{1}'", new Object[]{type, playerName});
            }
            return false;  // Return false if the preference already exists
        }
    }


    // Load the existing preferences from the player's file
    private Map<String, StringBuilder> loadExistingPreferences(File playerFile, String playerName) {
        Map<String, StringBuilder> preferences = new HashMap<>();
        for (String type : preferenceTriggers.keySet()) {
            preferences.put(type, new StringBuilder());
        }

        // Add "Randomly Saved Messages" section
        preferences.put("Randomly Saved Messages", new StringBuilder());

        if (playerFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(playerFile.toPath());
                String currentSection = null;

                for (String line : lines) {
                    for (String type : preferenceTriggers.keySet()) {
                        if (line.startsWith("**" + playerName + " " + capitalize(type) + "**")) {
                            currentSection = type;
                        }
                    }
                    // Check for the "Randomly Saved Messages" section
                    if (line.startsWith("**" + playerName + " Randomly Saved Messages**")) {
                        currentSection = "Randomly Saved Messages";
                    }
                    if (currentSection != null && !line.startsWith("**")) {
                        preferences.get(currentSection).append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to read preferences for player: " + playerName, e);
            }
        }
        return preferences;
    }

    // Write the preferences back to the player's file
    private void writePlayerPreferencesToFile(File playerFile, String playerName, Map<String, StringBuilder> preferences) {
        try (FileWriter writer = new FileWriter(playerFile)) {
            for (String type : preferences.keySet()) {
                writer.write("**" + playerName + " " + capitalize(type) + "**\n" + preferences.get(type).toString());
            }
            if (debugEnabled){ logger.log(Level.INFO, "Updated preferences for player: " + playerName); }
            // Schedule the reload command to run on the main server thread
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write preferences for player: " + playerName, e);
        }
    }

    // Remove conflicting preference from a section
    private StringBuilder removeConflictingPreference(StringBuilder section, String message) {
        String[] lines = section.toString().split("\n");
        StringBuilder updatedSection = new StringBuilder();
        for (String line : lines) {
        if (!line.trim().equalsIgnoreCase(message.trim())) {
                updatedSection.append(line).append("\n");
            }
        }
        return updatedSection;
    }

    private void reloadSAAIBasePlugin() {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (debugEnabled){ logger.log(Level.INFO, "Beginning Reload of SAAI"); }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ai reload");
            }
        });
    }
    private void scheduleDelayedReload() {
        // If there's already a reload task scheduled, cancel it and reschedule
        if (reloadTask != null) {
            reloadTask.cancel();
        }

        // Schedule a new reload after the delay
        reloadTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reloadSAAIBasePlugin();
            reloadTask = null; // Reset task after reload
        }, reloadDelayTicks);
    }
    // Capitalize the first letter of a word
    private String capitalize(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }

    private boolean isValidBlock(String blockName) {
        try {
            Material blockMaterial = Material.matchMaterial(blockName.toUpperCase().replace(" ", "_"));
            return blockMaterial != null && blockMaterial.isBlock();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
