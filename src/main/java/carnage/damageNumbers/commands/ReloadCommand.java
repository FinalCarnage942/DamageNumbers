package carnage.damageNumbers.commands;

import carnage.damageNumbers.DamageNumbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
    private final DamageNumbers plugin;

    public ReloadCommand(DamageNumbers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("damagenumbers.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        try {
            plugin.reload();
            sender.sendMessage(Component.text("DamageNumbers configuration reloaded.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error reloading: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Reload error: " + e.getMessage());
        }

        return true;
    }
}
