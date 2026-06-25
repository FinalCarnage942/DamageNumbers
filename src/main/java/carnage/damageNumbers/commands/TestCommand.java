package carnage.damageNumbers.commands;

import carnage.damageNumbers.DamageNumbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class TestCommand implements CommandExecutor {
    private final DamageNumbers plugin;

    public TestCommand(DamageNumbers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("test")) {
            player.sendMessage(Component.text("Usage: /damagenumbers test <hit|crit|heal>", NamedTextColor.RED));
            return true;
        }

        var loc = player.getLocation().add(0, 1.5, 0);
        var viewers = Set.of(player);
        var handler = plugin.getDamageHandler();
        var particles = plugin.getParticleHandler();

        switch (args[1].toLowerCase()) {
            case "hit" -> {
                handler.displayDamageHologram(loc, 5.0, false, viewers);
                particles.spawnHitParticles(player, player.getLocation(), false);
                player.sendMessage(Component.text("Displayed normal hit.", NamedTextColor.GREEN));
            }
            case "crit" -> {
                handler.displayDamageHologram(loc, 10.0, true, viewers);
                particles.spawnHitParticles(player, player.getLocation(), true);
                player.sendMessage(Component.text("Displayed crit hit.", NamedTextColor.GREEN));
            }
            case "heal" -> {
                handler.displayHealingHologram(player, loc, 5.0, viewers);
                player.sendMessage(Component.text("Displayed heal.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("Invalid type. Use: hit, crit, or heal.", NamedTextColor.RED));
        }

        return true;
    }
}
