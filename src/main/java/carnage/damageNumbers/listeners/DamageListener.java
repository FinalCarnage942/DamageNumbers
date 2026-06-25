package carnage.damageNumbers.listeners;

import carnage.damageNumbers.DamageConfig;
import carnage.damageNumbers.DamageNumbers;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DamageListener implements Listener {
    private final DamageNumbers plugin;
    private final Map<UUID, Long> lastHealTimes = new ConcurrentHashMap<>();

    public DamageListener(DamageNumbers plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        DamageConfig cfg = plugin.getDamageConfig();
        if (!plugin.getConfig().getBoolean("triggers.healing", true)) return;

        long now = System.currentTimeMillis();
        if (now - lastHealTimes.getOrDefault(player.getUniqueId(), 0L) < cfg.healCooldownMs) return;
        lastHealTimes.put(player.getUniqueId(), now);

        Set<Player> viewers = healViewers(player, cfg);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location loc = player.getLocation().add(0, 0.8, 0);
            plugin.getDamageHandler().displayHealingHologram(player, loc, event.getAmount(), viewers);
            viewers.forEach(v -> plugin.getParticleHandler().spawnHealingParticles(v, loc));
            playSound(viewers, player.getLocation(), cfg.soundHealing, cfg);
        }, cfg.healDelayTicks);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastHealTimes.remove(event.getPlayer().getUniqueId());
    }

    private void playSound(Set<Player> viewers, Location loc, String soundName, DamageConfig cfg) {
        if (soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            viewers.forEach(v -> v.playSound(loc, sound, cfg.soundVolume, cfg.soundPitch));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name: " + soundName);
        }
    }

    private Set<Player> healViewers(Player healer, DamageConfig cfg) {
        if (!"everyone".equals(cfg.healVisibilityMode)) return Set.of(healer);
        double r2 = cfg.healViewRange * cfg.healViewRange;
        Set<Player> viewers = new HashSet<>();
        healer.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distanceSquared(healer.getLocation()) <= r2)
            .forEach(viewers::add);
        return viewers;
    }
}
