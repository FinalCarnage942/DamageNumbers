package carnage.damageNumbers.commands;

import carnage.damageNumbers.DamageNumbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Handles the /dnreload command to reload the plugin's configuration.
 */
public class ReloadCommand implements CommandExecutor {
    private static final String PERMISSION_RELOAD = "damagenumbers.reload";

    private final DamageNumbers plugin;

    public ReloadCommand(DamageNumbers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendMessage(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        try {
            reloadPlugin();
            sendMessage(sender, Component.text("DamageNumbers configuration reloaded successfully!", NamedTextColor.GREEN));
            plugin.getPluginLogger().info(sender.getName() + " reloaded the configuration");
        } catch (Exception e) {
            sendMessage(sender, Component.text("Error reloading configuration: " + e.getMessage(), NamedTextColor.RED));
            plugin.getPluginLogger().warning("Error reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Reloads the plugin's configuration and handlers.
     */
    private void reloadPlugin() {
        plugin.reloadConfig();
        plugin.reloadHandler();
    }

    /**
     * Sends a message to the command sender.
     *
     * @param sender  the command sender
     * @param message the message to send
     */
    private void sendMessage(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }
}