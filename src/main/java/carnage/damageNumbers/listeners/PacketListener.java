package carnage.damageNumbers.listeners;

import carnage.damageNumbers.DamageNumberHandler;
import carnage.damageNumbers.DamageNumbers;
import carnage.damageNumbers.ParticleHandler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for damage events and packet events to display damage holograms and particles.
 */
public class PacketListener extends PacketListenerAbstract implements Listener {
    private static final String CONFIG_COOLDOWN = "cooldown-ms";
    private static final String CONFIG_STACKING_ENABLED = "advanced.stacking.enabled";
    private static final String CONFIG_STACKING_WINDOW = "advanced.stacking.window-ms";
    private static final String CONFIG_STACKING_DELAY = "advanced.stacking.delay-ticks";
    private static final String CONFIG_DELAY_TICKS = "advanced.delay-ticks";
    private static final String CONFIG_VISIBILITY_MODE = "display.visibility";
    private static final String CONFIG_VIEW_RANGE = "display.view-range";
    private static final String CONFIG_PVP_ENABLED = "triggers.player-vs-player";
    private static final String CONFIG_PVM_ENABLED = "triggers.player-vs-mob";
    private static final String CONFIG_MVP_ENABLED = "triggers.mob-vs-player";
    private static final String CONFIG_IGNORE_INVISIBLE = "triggers.ignore-invisible";
    private static final String CONFIG_IGNORED_TYPES = "triggers.ignored-entity-types";
    private static final String CONFIG_SOUND_NORMAL = "advanced.sounds.normal";
    private static final String CONFIG_SOUND_CRITICAL = "advanced.sounds.critical";

    private final DamageNumbers plugin;
    private final DamageNumberHandler damageNumberHandler;
    private final ParticleHandler particleHandler;
    private final Map<UUID, Long> lastDisplayTimes;
    private final Map<String, DamageStack> damageStacks;

    public PacketListener(DamageNumbers plugin, DamageNumberHandler damageNumberHandler, ParticleHandler particleHandler) {
        this.plugin = plugin;
        this.damageNumberHandler = damageNumberHandler;
        this.particleHandler = particleHandler;
        this.lastDisplayTimes = new ConcurrentHashMap<>();
        this.damageStacks = new ConcurrentHashMap<>();
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getPluginLogger().info("PacketListener initialized with PacketEvents " + PacketEvents.getAPI().getVersion());
    }

    /**
     * Inner class to manage stacked damage data.
     */
    private static class DamageStack {
        private double totalDamage;
        private long lastUpdateTime;
        private int taskId;
        private boolean hasCritical;

        DamageStack(double damage, long time, boolean critical) {
            this.totalDamage = damage;
            this.lastUpdateTime = time;
            this.taskId = -1;
            this.hasCritical = critical;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // No packet receive logic implemented
    }

    /**
     * Handles entity damage events to display damage holograms and particles.
     *
     * @param event the entity damage event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player damager = getDamager(event.getDamager());
        Entity target = event.getEntity();

        if (!shouldShowDamage(damager, target)) {
            return;
        }

        if (damager == null) {
            return;
        }

        if (isOnCooldown(damager.getUniqueId())) {
            plugin.getPluginLogger().fine("Damage display skipped for " + damager.getName() + " due to cooldown");
            return;
        }

        handleDamage(damager, target, event.getFinalDamage(), isCriticalHit(damager));
    }

    /**
     * Gets the damager as a Player, if applicable.
     *
     * @param damager the damaging entity
     * @return the Player damager, or null if not a player
     */
    private Player getDamager(Entity damager) {
        return damager instanceof Player ? (Player) damager : null;
    }

    /**
     * Determines if the damage is a critical hit.
     *
     * @param damager the damaging player
     * @return true if the hit is critical
     */
    private boolean isCriticalHit(Player damager) {
        return damager.getFallDistance() > 0 && damager.getVelocity().getY() < 0 && !damager.isOnGround();
    }

    /**
     * Checks if the damage hologram should be shown based on configuration.
     *
     * @param damager the damaging player, or null
     * @param target  the target entity
     * @return true if the hologram should be shown
     */
    private boolean shouldShowDamage(Player damager, Entity target) {
        if (damager == null && !(target instanceof Player)) {
            return false;
        }

        if (damager != null && target instanceof Player && !plugin.getConfig().getBoolean(CONFIG_PVP_ENABLED, true)) {
            return false;
        }

        if (damager != null && !(target instanceof Player) && !plugin.getConfig().getBoolean(CONFIG_PVM_ENABLED, true)) {
            return false;
        }

        if (damager == null && target instanceof Player && !plugin.getConfig().getBoolean(CONFIG_MVP_ENABLED, false)) {
            return false;
        }

        if (target instanceof LivingEntity living && living.isInvisible() && plugin.getConfig().getBoolean(CONFIG_IGNORE_INVISIBLE, true)) {
            return false;
        }

        return !plugin.getConfig().getStringList(CONFIG_IGNORED_TYPES).contains(target.getType().name());
    }

    /**
     * Checks if the player is on cooldown for damage holograms.
     *
     * @param playerId the player's UUID
     * @return true if the player is on cooldown
     */
    private boolean isOnCooldown(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong(CONFIG_COOLDOWN, 50);
        Long lastDisplayTime = lastDisplayTimes.getOrDefault(playerId, 0L);
        if (currentTime - lastDisplayTime < cooldownMs) {
            return true;
        }
        lastDisplayTimes.put(playerId, currentTime);
        return false;
    }

    /**
     * Handles the damage event, either stacking or displaying immediately.
     *
     * @param damager    the damaging player
     * @param target     the target entity
     * @param damage     the damage amount
     * @param isCritical whether the hit is critical
     */
    private void handleDamage(Player damager, Entity target, double damage, boolean isCritical) {
        plugin.getPluginLogger().info("Damage event: " + damager.getName() + " dealt " + damage + " to " + target.getType() + ", Critical: " + isCritical + ", FallDistance: " + damager.getFallDistance() + ", VelocityY: " + damager.getVelocity().getY());
        Set<Player> viewers = getViewers(damager, target);

        if (plugin.getConfig().getBoolean(CONFIG_STACKING_ENABLED, false)) {
            handleStackedDamage(damager, target, damage, isCritical, viewers);
        } else {
            scheduleDamageDisplay(damager, target, damage, isCritical, viewers);
        }
    }

    /**
     * Schedules a damage hologram and particle display.
     *
     * @param damager    the damaging player
     * @param target     the target entity
     * @param damage     the damage amount
     * @param isCritical whether the hit is critical
     * @param viewers    the players to see the hologram
     */
    private void scheduleDamageDisplay(Player damager, Entity target, double damage, boolean isCritical, Set<Player> viewers) {
        int delayTicks = plugin.getConfig().getInt(CONFIG_DELAY_TICKS, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            damageNumberHandler.displayDamageHologram(damager, getHologramLocation(target), damage, isCritical, viewers);
            particleHandler.spawnHitParticles(damager, target.getLocation(), isCritical);
            playDamageSound(viewers, target.getLocation(), isCritical);
        }, delayTicks);
    }

    /**
     * Handles stacked damage for delayed display.
     *
     * @param damager    the damaging player
     * @param target     the target entity
     * @param damage     the damage amount
     * @param isCritical whether the hit is critical
     * @param viewers    the players to see the hologram
     */
    private void handleStackedDamage(Player damager, Entity target, double damage, boolean isCritical, Set<Player> viewers) {
        long currentTime = System.currentTimeMillis();
        String stackKey = damager.getUniqueId() + ":" + target.getUniqueId();
        long stackWindow = plugin.getConfig().getLong(CONFIG_STACKING_WINDOW, 300);

        DamageStack stack = damageStacks.computeIfAbsent(stackKey, k -> new DamageStack(damage, currentTime, isCritical));

        if (currentTime - stack.lastUpdateTime < stackWindow) {
            stack.totalDamage += damage;
            stack.lastUpdateTime = currentTime;
            if (isCritical) {
                stack.hasCritical = true;
            }

            if (stack.taskId != -1) {
                plugin.getServer().getScheduler().cancelTask(stack.taskId);
            }
        } else {
            stack.totalDamage = damage;
            stack.lastUpdateTime = currentTime;
            stack.hasCritical = isCritical;
        }

        stack.taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            damageNumberHandler.displayDamageHologram(damager, getHologramLocation(target), stack.totalDamage, stack.hasCritical, viewers);
            particleHandler.spawnHitParticles(damager, target.getLocation(), stack.hasCritical);
            playDamageSound(viewers, target.getLocation(), stack.hasCritical);
            damageStacks.remove(stackKey);
        }, plugin.getConfig().getInt(CONFIG_STACKING_DELAY, 5)).getTaskId();
    }

    /**
     * Gets the location for hologram display, offset above the target.
     *
     * @param target the target entity
     * @return the hologram location
     */
    private Location getHologramLocation(Entity target) {
        return target.getLocation().add(0, 0.8, 0);
    }

    /**
     * Plays the configured sound for damage events.
     *
     * @param viewers    the players to hear the sound
     * @param location   the sound location
     * @param isCritical whether the hit is critical
     */
    private void playDamageSound(Set<Player> viewers, Location location, boolean isCritical) {
        String soundName = isCritical ? plugin.getConfig().getString(CONFIG_SOUND_CRITICAL, "") : plugin.getConfig().getString(CONFIG_SOUND_NORMAL, "");
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
     * @param damager the damaging player
     * @param target  the target entity
     * @return the set of viewers
     */
    private Set<Player> getViewers(Player damager, Entity target) {
        Set<Player> viewers = new HashSet<>();
        String visibilityMode = plugin.getConfig().getString(CONFIG_VISIBILITY_MODE, "damager").toLowerCase();
        double viewRange = plugin.getConfig().getDouble(CONFIG_VIEW_RANGE, 32.0);

        if ("everyone".equals(visibilityMode)) {
            target.getWorld().getPlayers().stream()
                    .filter(nearby -> nearby.getLocation().distanceSquared(target.getLocation()) <= viewRange * viewRange)
                    .forEach(viewers::add);
        } else {
            viewers.add(damager);
        }

        return viewers;
    }
}