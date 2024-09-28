package com.hearty.playercommandhandler;

import dev.bluetree242.serverassistantai.api.events.PreMinecraftHandleEvent;
import dev.bluetree242.serverassistantai.api.events.PostMinecraftHandleEvent;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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

public class PlayerCommandHandler implements Listener {
    private final JavaPlugin plugin;
    private final Logger logger = Logger.getLogger("SAAI-PlayerData");
    private Map<String, String> allowedCommands;
    private final Map<String, String> assistanceRegex = new HashMap<>();
    private final ConfigManager configManager;
    private boolean debugEnabled;
    private Set<String> complexCommands;
    private Set<String> requiresAdmin;

    public PlayerCommandHandler(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        initializeConfig();
    }
    public void reloadCommands() {
        ClearExisting();
        initializeConfig();
    }
    public void ClearExisting() {
        this.allowedCommands.clear();
        this.assistanceRegex.clear();
        this.complexCommands.clear();
        this.requiresAdmin.clear();
    }
    public void initializeConfig() {
        this.allowedCommands = configManager.getAllowedCommands();
        this.assistanceRegex.putAll(configManager.getAssistanceRegex());
        this.debugEnabled = configManager.getDebugMode();
        this.complexCommands = configManager.getComplexCommands();
        this.requiresAdmin = configManager.getCommandRequirements();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = preprocessMessage(event.getMessage());
        String playerName = event.getPlayer().getName();
        String commandMatched = processMessageForCommand(playerName, message);
        if (commandMatched != null){

            boolean isComplexCommand = complexCommands.contains(commandMatched);
            boolean requiresAdmin = complexCommands.contains(commandMatched);
            //logger.log(Level.WARNING,commandMatched + " Requires Admin:" + requiresAdmin + ". Is Complex: " + isComplexCommand);

            String command = processMessageForAssistance(playerName, message, commandMatched);

            if (command != null){
                attemptCommandExecuteForPlayer(command, playerName, isComplexCommand, message);
            }
        }
    }

    @EventHandler
    public void onPreMinecraftHandle(PreMinecraftHandleEvent event) {
        String message = preprocessMessage(event.getMessage());
        String playerName = event.getPlayer().getName();
        String commandMatched = processMessageForCommand(playerName, message);
        if (commandMatched != null){

            boolean isComplexCommand = complexCommands.contains(commandMatched);
            boolean requiresAdmin = complexCommands.contains(commandMatched);
            logger.log(Level.WARNING,commandMatched + " Requires Admin:" + requiresAdmin + ". Is Complex: " + isComplexCommand);

            String command = processMessageForAssistance(playerName, message, commandMatched);

            if (command != null){
                attemptCommandExecuteForPlayer(command, playerName, isComplexCommand, message);
            }
        }
    }

    //@EventHandler
    //public void onPostMinecraftHandle(PostMinecraftHandleEvent event) {
    //
    //}

    private String preprocessMessage(String message) {
        return message.toLowerCase().replaceAll("[?!.]+$", "").trim();
    }

    private String processMessageForCommand(String playerName, String message) {   
        String[] words = message.split("\\s+"); // Split the message by whitespace
    
        // First, check if the command matches a complex command
        for (String word : words) {
            if (complexCommands.contains(word)) { // Check if it's a complex command
                //logger.log(Level.WARNING, "Found Complex Command: " + word, new Object[]{word, playerName});
                return word;
            }
        }

        // If no complex command is matched, check for simple commands in allowedCommands
        for (String word : words) {
            if (allowedCommands.containsKey(word)) { // Check the key in the map
                //logger.log(Level.WARNING, "Found Simple Command: " + word, new Object[]{word, playerName});
                return word;
            }
        }

        // Return null if no matching command was found
        return null;
    }


    private String processMessageForAssistance(String playerName, String message, String command) {

        // Iterate over each regex pattern in the map
        for (Map.Entry<String, String> entry : assistanceRegex.entrySet()) {
            String regexPattern = entry.getKey();
            String assistanceType = entry.getValue();  // The key or identifier for the assistance type

            // Compile the regex pattern from the string
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);  // Case-insensitive search
            Matcher matcher = pattern.matcher(message);

            // Check if the message matches the assistance pattern
            if (matcher.find()) {
                if (assistanceType.equals(command)){
                    // Log the first matched word or phrase and the assistance type
                    String matchedPhrase = matcher.group();
                    logger.log(Level.WARNING, "Confirmed " + playerName + " is asking for assistance: " + assistanceType,
                               new Object[]{matchedPhrase, playerName});
                    return assistanceType;
                } else {
                    logger.log(Level.WARNING, command + " does not match: " + assistanceType);
                }
            }
        }
        //logger.log(Level.WARNING, playerName + " is likely not asking for assistance.. Ignoring");
        // Return false if no matches are found
        return null;
    }
    private void attemptCommandExecuteForPlayer(String commandSection, String playerName, boolean complex, String message) {
        String commandTemplate;

        if (complex) {
            // If it's a complex command, get the template from complexCommands directly
            commandTemplate = complexCommands.contains(commandSection) ? commandSection : null;
        
            if (commandTemplate != null) {
                commandTemplate = buildComplexCommandTemplate(commandTemplate, message); // Build full command with dynamic placeholders
            }
        } else {
            // For simple commands, fetch the command template from allowedCommands
            commandTemplate = allowedCommands.get(commandSection);
            if (commandTemplate != null) {
                commandTemplate = replacePlaceholders(commandTemplate, playerName);
            }
        }

        // If we successfully built the command template, execute it
        if (commandTemplate != null) {
            final String formattedCommand = commandTemplate;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                }
            });
        } else {
            logger.log(Level.WARNING, "No valid command template found for: " + commandSection);
        }
    }
    private String replacePlaceholders(String command, String playerName) {
        command = command.replace("%player%", playerName);  // Replace $player% with the player's name
        // Replace $player% with actual player's name
        return command;  // Replace "playerName" with the actual player's name
    }

    private String buildComplexCommandTemplate(String commandTemplate, String message) {
        // Extract the placeholders from the command template (e.g., %user%, %reason%)
        Pattern placeholderPattern = Pattern.compile("%(\\w+)%");
        Matcher placeholderMatcher = placeholderPattern.matcher(commandTemplate);

        // Tokenize the message to match against the placeholders
        String[] messageTokens = message.split("\\s+");

        // Create a map to hold the dynamic values like user and reason
        Map<String, String> extractedValues = new HashMap<>();

        // Only match for the first placeholder like %user%, rest goes to %reason%
        if (placeholderMatcher.find()) {
            String firstPlaceholder = placeholderMatcher.group(1); // Get the first placeholder (likely %user%)

            // Assume the first relevant token is the user
            if (messageTokens.length > 1) {
                extractedValues.put(firstPlaceholder, messageTokens[1]); // The user is the first token after command
            }

            // If there is another placeholder (like %reason%), capture the rest of the message
            if (placeholderMatcher.find()) {
                String secondPlaceholder = placeholderMatcher.group(1); // Get the second placeholder (likely %reason%)

                // Join the remaining message tokens as the reason
                String reason = String.join(" ", Arrays.copyOfRange(messageTokens, 2, messageTokens.length));
                extractedValues.put(secondPlaceholder, reason); // Put the entire remaining message as the reason
            }
        }

        // Replace placeholders in the command template with the extracted values
        for (Map.Entry<String, String> entry : extractedValues.entrySet()) {
            commandTemplate = commandTemplate.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return commandTemplate;
    }


}
