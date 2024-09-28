package com.hearty.appenddatatoprompt;

import dev.bluetree242.serverassistantai.api.events.PreMinecraftHandleEvent;
import dev.bluetree242.serverassistantai.api.events.PostMinecraftHandleEvent;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.hearty.configmanager.ConfigManager;

public class AppendDataToPrompt implements Listener {
    private final File documentFolder;
    private final File informationFile;
    private final JavaPlugin plugin;
    private final Logger logger = Logger.getLogger("SAAI-PlayerData");
    private String originalContent = ""; // Store the original content before appending
    private final Map<String, String> preferenceSections = new HashMap<>();
    private final ConfigManager configManager;
    private boolean debugEnabled;

    public AppendDataToPrompt(File documentFolder, File promptFolder, JavaPlugin plugin, ConfigManager configManager) {
        this.documentFolder = documentFolder;
        this.informationFile = new File(promptFolder, "information-message.txt");
        this.plugin = plugin;
        this.configManager = configManager;

        // Initialize preference sections from config
        initializeConfig();
    }
    public void reloadSections() {
        ClearExisting();
        initializeConfig();  // Initialize preference triggers
    }

    public void ClearExisting() {
        this.preferenceSections.clear();
    }

    public void initializeConfig() {
        this.preferenceSections.putAll(configManager.getPreferenceSections());
        this.debugEnabled = configManager.getDebugMode();
    }

    @EventHandler
    public void onPreMinecraftHandle(PreMinecraftHandleEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        File playerFile = new File(documentFolder, playerName + ".txt");

        if (!playerFile.exists()) {
            logger.warning("Player data file for " + playerName + " does not exist.");
            return;
        }

        // Backup the original file content before appending new data
        backupOriginalContent();

        // Build the message to append to the file
        StringBuilder message = new StringBuilder("\n\nBelow is some information about " + playerName + ", who you are currently talking to. you should respond based on this infomation:\n");

        // Loop through preference sections
        for (Map.Entry<String, String> entry : preferenceSections.entrySet()) {
            String preferenceType = entry.getKey();  // This is now the message
            String sectionMessage = entry.getValue();  // This is the section name
            String preferenceData = extractPreferenceSection(playerFile, playerName, preferenceType);
        
            // Only append sections that have data
            if (preferenceData != null && !preferenceData.trim().isEmpty()) {
                message.append(sectionMessage).append(": ").append(preferenceData).append("\n");
            }
        }

        // Append the formatted message to the file
        appendToFile(message.toString());
    }

    @EventHandler
    public void onPostMinecraftHandle(PostMinecraftHandleEvent event) {
        restoreOriginalContent();
    }

    private void appendToFile(String message) {
        try (FileWriter writer = new FileWriter(informationFile, true)) {
            writer.write(message + "\n");
        } catch (IOException e) {
            logger.severe("Failed to append to the information file.");
            e.printStackTrace();
        }
    }

    private void backupOriginalContent() {
        try {
            if (informationFile.exists()) {
                originalContent = new String(Files.readAllBytes(informationFile.toPath()));
            }
        } catch (IOException e) {
            logger.severe("Failed to backup the original content of the file.");
            e.printStackTrace();
        }
    }

    private void restoreOriginalContent() {
        try (FileWriter writer = new FileWriter(informationFile, false)) {
            writer.write(originalContent);
            logger.info("Restored the original content of the file.");
        } catch (IOException e) {
            logger.severe("Failed to restore the original content of the file.");
            e.printStackTrace();
        }
    }

    private String extractPreferenceSection(File playerFile, String playerName, String preferenceType) {
        StringBuilder preferenceData = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(playerFile.toPath());
            boolean isInSection = false;
            for (String line : lines) {
                if (line.startsWith("**" + playerName + " " + preferenceType + "**")) {
                    isInSection = true;
                } else if (line.startsWith("**") && isInSection) {
                    break;  // Stop if we encounter the next section
                } else if (isInSection) {
                    preferenceData.append(line).append(", ");
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to read player preferences for " + playerName);
            e.printStackTrace();
        }
        return preferenceData.length() > 0 ? preferenceData.toString().trim() : null;
    }
}
