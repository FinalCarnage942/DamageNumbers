package carnage.damageNumbers.listeners;

import carnage.damageNumbers.DamageNumberHandler;
import carnage.damageNumbers.DamageNumbers;
import carnage.damageNumbers.ParticleHandler;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for healing events and displays healing holograms with particles.
 */
public class DamageListener implements Listener {
    private static final String CONFIG_HEALING_ENABLED = "triggers.healing";
    private static final String CONFIG_HEALING_COOLDOWN = "healing.cooldown-ms";
    private static final String CONFIG_HEALING_DELAY = "advanced.healing-delay-ticks";
    private static final String CONFIG_HEALING_SOUND = "advanced.sounds.healing";
    private static final String CONFIG_VISIBILITY_MODE = "healing.visibility";
    private static final String CONFIG_VIEW_RANGE = "healing.view-range";

    private final DamageNumbers plugin;
    private final DamageNumberHandler damageNumberHandler;
    private final ParticleHandler particleHandler;
    private final Map<UUID, Long> lastHealTimes;

    public DamageListener(DamageNumbers plugin, DamageNumberHandler damageNumberHandler, ParticleHandler particleHandler) {
        this.plugin = plugin;
        this.damageNumberHandler = damageNumberHandler;
        this.particleHandler = particleHandler;
        this.lastHealTimes = new ConcurrentHashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getPluginLogger().info("DamageListener registered for healing events");
    }

    /**
     * Handles healing events to display holograms and particles.
     *
     * @param event the entity regain health event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!isHealingEnabled()) {
            return;
        }

        if (isOnCooldown(player.getUniqueId())) {
            plugin.getPluginLogger().fine("Healing display skipped for " + player.getName() + " due to cooldown");
            return;
        }

        handleHealing(player, event.getAmount());
    }

    /**
     * Checks if healing holograms are enabled in the configuration.
     *
     * @return true if healing holograms are enabled
     */
    private boolean isHealingEnabled() {
        return plugin.getConfig().getBoolean(CONFIG_HEALING_ENABLED, true);
    }

    /**
     * Checks if the player is on cooldown for healing holograms.
     *
     * @param playerId the player's UUID
     * @return true if the player is on cooldown
     */
    private boolean isOnCooldown(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong(CONFIG_HEALING_COOLDOWN, 50);
        Long lastHealTime = lastHealTimes.getOrDefault(playerId, 0L);
        if (currentTime - lastHealTime < cooldownMs) {
            return true;
        }
        lastHealTimes.put(playerId, currentTime);
        return false;
    }

    /**
     * Handles the healing event by scheduling hologram and particle display.
     *
     * @param player the healed player
     * @param amount the amount of health regained
     */
    private void handleHealing(Player player, double amount) {
        plugin.getPluginLogger().info("Healing event: " + player.getName() + " regained " + amount + " health, reason: " + amount);
        Set<Player> viewers = getViewers(player);
        int delayTicks = plugin.getConfig().getInt(CONFIG_HEALING_DELAY, 0);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            damageNumberHandler.displayHealingHologram(player, getHologramLocation(player), amount, viewers);
            viewers.forEach(viewer -> particleHandler.spawnHealingParticles(viewer, getHologramLocation(player)));
            playHealingSound(viewers, player.getLocation());
            plugin.getPluginLogger().fine("Healing hologram shown to " + viewers.size() + " viewers for " + player.getName());
        }, delayTicks);
    }

    /**
     * Gets the location for hologram display, offset above the player.
     *
     * @param player the player
     * @return the hologram location
     */
    private Location getHologramLocation(Player player) {
        return player.getLocation().add(0, 0.8, 0);
    }

    /**
     * Plays the configured healing sound to viewers.
     *
     * @param viewers  the players to hear the sound
     * @param location the sound location
     */
    private void playHealingSound(Set<Player> viewers, Location location) {
        String soundName = plugin.getConfig().getString(CONFIG_HEALING_SOUND, "");
        if (soundName.isEmpty()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            float volume = (float) plugin.getConfig().getDouble("advanced.sounds.volume", 0.5);
            float pitch = (float) plugin.getConfig().getDouble("advanced.sounds.pitch", 1.0);
            viewers.forEach(viewer -> viewer.playSound(location, sound, volume, pitch));
        } catch (IllegalArgumentException e) {
            plugin.getPluginLogger().warning("Invalid sound name: " + soundName);
        }
    }

    /**
     * Gets the set of players who should see the hologram.
     *
     * @param healer the healed player
     * @return the set of viewers
     */
    private Set<Player> getViewers(Player healer) {
        Set<Player> viewers = new HashSet<>();
        String visibilityMode = plugin.getConfig().getString(CONFIG_VISIBILITY_MODE, "healer").toLowerCase();
        double viewRange = plugin.getConfig().getDouble(CONFIG_VIEW_RANGE, 32.0);

        if ("everyone".equals(visibilityMode)) {
            healer.getWorld().getPlayers().stream()
                    .filter(nearby -> nearby.getLocation().distanceSquared(healer.getLocation()) <= viewRange * viewRange)
                    .forEach(viewers::add);
        } else {
            viewers.add(healer);
        }

        return viewers;
    }
}