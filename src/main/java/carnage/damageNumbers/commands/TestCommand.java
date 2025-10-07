package carnage.damageNumbers.commands;

import carnage.damageNumbers.DamageNumberHandler;
import carnage.damageNumbers.DamageNumbers;
import carnage.damageNumbers.ParticleHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles the /damagenumbers test command to display test damage or healing holograms.
 */
public class TestCommand implements CommandExecutor {
    private static final String USAGE_MESSAGE = "Usage: /damagenumbers test <hit|crit|heal>";

    private final DamageNumbers plugin;
    private final DamageNumberHandler damageHandler;
    private final ParticleHandler particleHandler;

    public TestCommand(DamageNumbers plugin, DamageNumberHandler damageHandler, ParticleHandler particleHandler) {
        this.plugin = plugin;
        this.damageHandler = damageHandler;
        this.particleHandler = particleHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("test")) {
            sendMessage(player, Component.text(USAGE_MESSAGE, NamedTextColor.RED));
            return true;
        }

        handleTestCommand(player, args[1].toLowerCase());
        return true;
    }

    /**
     * Processes the test command based on the specified type.
     *
     * @param player the player executing the command
     * @param type   the type of test (hit, crit, or heal)
     */
    private void handleTestCommand(Player player, String type) {
        Set<Player> viewers = new HashSet<>();
        viewers.add(player);

        switch (type) {
            case "hit" -> {
                damageHandler.displayDamageHologram(player, getHologramLocation(player), 5.0, false, viewers);
                particleHandler.spawnHitParticles(player, player.getLocation(), false);
                sendMessage(player, Component.text("Displayed normal hit damage number.", NamedTextColor.GREEN));
            }
            case "crit" -> {
                damageHandler.displayDamageHologram(player, getHologramLocation(player), 10.0, true, viewers);
                particleHandler.spawnHitParticles(player, player.getLocation(), true);
                sendMessage(player, Component.text("Displayed critical hit damage number.", NamedTextColor.GREEN));
            }
            case "heal" -> {
                damageHandler.displayHealingHologram(player, getHologramLocation(player), 5.0, viewers);
                sendMessage(player, Component.text("Displayed heal damage number.", NamedTextColor.GREEN));
            }
            default -> sendMessage(player, Component.text("Invalid type. Use: hit, crit, or heal.", NamedTextColor.RED));
        }
    }

    /**
     * Gets the location for hologram display, offset above the player.
     *
     * @param player the player
     * @return the hologram location
     */
    private org.bukkit.Location getHologramLocation(Player player) {
        return player.getLocation().add(0, 1.5, 0);
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