package com.hearty.savedinfocommandhandler;

import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.hearty.configmanager.ConfigManager;

public class SavedInfoCommandHandler implements CommandExecutor {
    private final File documentFolder;
    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public SavedInfoCommandHandler(File documentFolder, JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.documentFolder = documentFolder;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            Player player = (Player) sender;
            String playerName;

            // Case 1: No arguments provided, show info for the player who ran the command
            if (args.length == 0) {
                if (sender.hasPermission("aipd.savedinfo")){
                    playerName = player.getName(); // The player name passed as argument
                } else {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to view Saved Info!");
                    return true;
                }
            } else if (args.length == 1) {
                if (sender.hasPermission("aipd.savedinfo.others")){
                    playerName = args[0]; // The player name passed as argument
                } else {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to view others Saved Info! Use /savedinfo");
                    return true;
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid usage! Use /savedinfo [playername]");
                return true;
            }

            // Load the player's saved information
            File playerFile = new File(documentFolder, playerName + ".txt");
            if (!playerFile.exists()) {
                player.sendMessage(ChatColor.RED + "You have no saved information.");
                return true;
            }
            ChatColor playerColor = getPlayerColor(playerName); // Get the player's preferred color
            if (playerColor == ChatColor.BLACK){
                playerColor = ChatColor.WHITE;
            }

            try {
                List<String> lines = Files.readAllLines(playerFile.toPath());
                StringBuilder combinedInfo = new StringBuilder();  // To accumulate regular information
                boolean lastLineWasHeader = false;  // To track if the previous line was a header
                String lastHeader = null;           // To temporarily store the last header

                // Create a fancy header
                player.sendMessage(ChatColor.DARK_GRAY + "==============================");
                player.sendMessage(ChatColor.WHITE + playerName + " | Saved Information");
                player.sendMessage(ChatColor.DARK_GRAY + "==============================");

                // Display each line with special formatting for headers
                for (int i = 0; i < lines.size(); i++) {
                    String currentLine = lines.get(i);
                    String nextLine = (i + 1 < lines.size()) ? lines.get(i + 1) : null; // Get the next line, if it exists

                    if (currentLine.startsWith("**") && currentLine.endsWith("**")) {
                        // Format as a header (likes, dislikes, etc.)
                        String header = playerColor + currentLine.substring(2, currentLine.length() - 2) + ":"; // Remove ** and change color
                        // Check if the next line is empty or a section
                        if (nextLine == null || nextLine.trim().isEmpty() || (nextLine.startsWith("**") && nextLine.endsWith("**"))) {
                            // Skip this header if the next line is empty or another header
                            continue;
                        }
                        // If there is accumulated info, send it before the new header
                        if (combinedInfo.length() > 0) {
                            player.sendMessage(ChatColor.WHITE + "  " + ChatColor.GRAY + combinedInfo.toString());
                            combinedInfo.setLength(0); // Clear accumulated info for the next section
                        }

                        player.sendMessage(ChatColor.BOLD + header);

                    } else {
                        // Regular information
                        if (combinedInfo.length() > 0) {
                            combinedInfo.append(ChatColor.DARK_GRAY + " | " + ChatColor.GRAY); // Add a comma before new information if there's already info
                        }
                        combinedInfo.append(currentLine.trim()); // Accumulate the regular information
                    }
                }

                // After the loop, send any remaining accumulated info
                if (combinedInfo.length() > 0) {
                    player.sendMessage(ChatColor.WHITE + "  " + ChatColor.GRAY + combinedInfo.toString());
                }
                // End border
                player.sendMessage(ChatColor.DARK_GRAY + "==============================");
                

            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Failed to read your saved information.");
                e.printStackTrace();
            }

            return true;
    }
    public ChatColor getPlayerColor(String playerName) {
        File playerFile = new File(documentFolder, playerName + ".txt");
    
        if (!playerFile.exists()) {
            return ChatColor.WHITE; // Fallback color if the file doesn't exist
        }

        try {
            List<String> lines = Files.readAllLines(playerFile.toPath());
            for (String line : lines) {
                if (line.startsWith("**" + playerName + " Color**")) {
                    // Get the next line which should contain the color preference
                    int index = lines.indexOf(line) + 1;
                    if (index < lines.size()) {
                        String colorLine = lines.get(index);
                        return parseColor(colorLine);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ChatColor.WHITE; // Fallback color if no color found
    }

    private ChatColor parseColor(String colorLine) {
        Set<String> bukkitColors = new HashSet<>();
        for (ChatColor color : ChatColor.values()) {
            bukkitColors.add(color.name().toLowerCase()); // Add all Bukkit color names to the set
        }



        String[] words = colorLine.split("\\s+"); // Split by whitespace
        // First, check for exact matches
        for (String word : words) {
            if (bukkitColors.contains(word)) {
                return ChatColor.valueOf(word.toUpperCase()); // Return the matching ChatColor
            }
        }
        
        // If no exact match is found, look for the closest color
        String closestColor = findClosestColor(words, bukkitColors);
        if (closestColor != null) {
            return ChatColor.valueOf(closestColor.toUpperCase());
        }

        return ChatColor.WHITE; // Fallback color if no match found

    }

    private String findClosestColor(String[] words, Set<String> bukkitColors) {
        String closestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String word : words) {
            for (String bukkitColor : bukkitColors) {
                int distance = levenshteinDistance(word, bukkitColor);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestMatch = bukkitColor;
                }
            }
        }

        return closestMatch; // Return the closest match if found
    }

    // Levenshtein Distance Algorithm
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j; // Deleting characters from b
                } else if (j == 0) {
                    dp[i][j] = i; // Inserting characters into b
                } else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), // Deletion or insertion
                            dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1) // Substitution
                    );
                }
            }
        }

        return dp[a.length()][b.length()]; // The distance is in the bottom-right corner
    }
}
