package com.hearty.playerdataaddon;

import io.papermc.lib.PaperLib;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.*;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.util.DiscordUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.hearty.playerchatlistener.PlayerChatListener;
import com.hearty.appenddatatoprompt.AppendDataToPrompt;
import com.hearty.playercommandhandler.PlayerCommandHandler;
import com.hearty.savedinfocommandhandler.SavedInfoCommandHandler;
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
    private PlayerCommandHandler playerCommandHandler;
    private SavedInfoCommandHandler savedInfoCommandHandler;

    private final Logger logger = Logger.getLogger("SAAI-PlayerData");
    private File saaiDocumentsFolder;

    public boolean triggersEnabled;
    public boolean promptsEnabled;
    public boolean commandsEnabled;
    public boolean discordEnabled;

    @Override
    public void onEnable() {

        // Initialize the configuration manager
        configManager = new ConfigManager(this);
        initializeConfigStates();

        File saaiPlayerDocumentsFolder = new File(getDataFolder().getParentFile(), "ServerAssistantAI/documents/players");
        if (!saaiPlayerDocumentsFolder.exists()) {
            saaiPlayerDocumentsFolder.mkdirs();
        }

        if(triggersEnabled){
            playerChatListener = new PlayerChatListener(saaiPlayerDocumentsFolder, this, configManager);
            getServer().getPluginManager().registerEvents(playerChatListener, this);
        }
        
        if(promptsEnabled){
            File saaiPromptFolder = new File(getDataFolder().getParentFile(), "ServerAssistantAI/minecraft");
            appendDataToPrompt = new AppendDataToPrompt(saaiPlayerDocumentsFolder, saaiPromptFolder, this, configManager);
            getServer().getPluginManager().registerEvents(appendDataToPrompt, this);
        }

        if(commandsEnabled){
            playerCommandHandler = new PlayerCommandHandler(this, configManager);
            getServer().getPluginManager().registerEvents(playerCommandHandler, this);
        
            savedInfoCommandHandler = new SavedInfoCommandHandler(saaiPlayerDocumentsFolder, this, configManager);
            getCommand("savedinfo").setExecutor(savedInfoCommandHandler); // Registering the command
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("aipd")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {

                configManager.loadConfig(); // Reload the config
                initializeConfigStates();

                this.playerChatListener.reloadPreferences();
                this.appendDataToPrompt.reloadSections();
                this.playerCommandHandler.reloadCommands();

                sender.sendMessage("Plugin has reloaded.");
                return true;
            }
        }
        return false;
    }
    public void initializeConfigStates(){
        triggersEnabled = configManager.getStateForTriggers();
        promptsEnabled = configManager.getStateForPrompts();
        commandsEnabled = configManager.getStateForCommands();
        discordEnabled = configManager.getStateForDiscord();
    }
}
