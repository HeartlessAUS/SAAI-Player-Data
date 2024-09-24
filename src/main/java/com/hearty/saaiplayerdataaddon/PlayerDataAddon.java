package com.hearty.playerdataaddon;

import io.papermc.lib.PaperLib;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.hearty.playerchatlistener.PlayerChatListener;
import com.hearty.appenddatatoprompt.AppendDataToPrompt;
import com.hearty.configmanager.ConfigManager;

/**
 * Created by Levi Muniz on 7/29/20.
 *
 * @author Copyright (c) Levi Muniz. All Rights Reserved.
 */
public class PlayerDataAddon extends JavaPlugin {
    private ConfigManager configManager;
    private PlayerChatListener playerChatListener;
    private AppendDataToPrompt appendDataToPrompt;

    private File saaiDocumentsFolder;
    private final Logger logger = Logger.getLogger("SAAI-PlayerData");
    @Override
    public void onEnable() {
        // Initialize the SAAI Documents folder

        File saaiDocumentsFolder = new File(getDataFolder().getParentFile(), "ServerAssistantAI/documents/players");
        File saaiPromptFolder = new File(getDataFolder().getParentFile(), "ServerAssistantAI/minecraft");
        if (!saaiDocumentsFolder.exists()) {
            saaiDocumentsFolder.mkdirs();
        }

        // Initialize the configuration manager
        configManager = new ConfigManager(this);

        playerChatListener = new PlayerChatListener(saaiDocumentsFolder, this, configManager); // Store the instance
        getServer().getPluginManager().registerEvents(playerChatListener, this);
        appendDataToPrompt = new AppendDataToPrompt(saaiDocumentsFolder, saaiPromptFolder, this, configManager);
        getServer().getPluginManager().registerEvents(appendDataToPrompt, this);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("aipd")) {
            configManager.loadConfig(); // Reload the config
            this.playerChatListener.reloadPreferences();
            this.appendDataToPrompt.reloadSections();
            // Optionally reload the preference triggers in PlayerChatListener
            // Note: You might need to pass a new instance or modify the existing one
            // Example: playerChatListener.reloadPreferences(configManager.getPreferenceTriggers());

            sender.sendMessage("Preferences have been reloaded.");
            return true;
        }
        return false;
    }
}
