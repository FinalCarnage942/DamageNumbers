package carnage.damageNumbers.listeners;

import carnage.damageNumbers.DamageConfig;
import carnage.damageNumbers.DamageNumbers;
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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketListener extends PacketListenerAbstract implements Listener {
    private final DamageNumbers plugin;
    private final Map<UUID, Long> lastDisplayTimes = new ConcurrentHashMap<>();
    private final Map<String, DamageStack> damageStacks = new ConcurrentHashMap<>();

    public PacketListener(DamageNumbers plugin) {
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static class DamageStack {
        double totalDamage;
        long lastUpdateTime;
        int taskId = -1;
        boolean hasCritical;

        DamageStack(double damage, long time, boolean critical) {
            this.totalDamage = damage;
            this.lastUpdateTime = time;
            this.hasCritical = critical;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {}

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;

        DamageConfig cfg = plugin.getDamageConfig();
        Entity target = event.getEntity();
        if (!shouldShow(damager, target, cfg)) return;

        long now = System.currentTimeMillis();
        if (now - lastDisplayTimes.getOrDefault(damager.getUniqueId(), 0L) < cfg.cooldownMs) return;
        lastDisplayTimes.put(damager.getUniqueId(), now);

        boolean isCritical = damager.getFallDistance() > 0 && damager.getVelocity().getY() < 0 && !damager.isOnGround();
        Set<Player> viewers = damageViewers(damager, target, cfg);

        if (cfg.stackingEnabled) {
            stackDamage(damager, target, event.getFinalDamage(), isCritical, viewers, cfg);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location loc = target.getLocation().add(0, 0.8, 0);
                plugin.getDamageHandler().displayDamageHologram(loc, event.getFinalDamage(), isCritical, viewers);
                plugin.getParticleHandler().spawnHitParticles(damager, target.getLocation(), isCritical);
                playSound(viewers, target.getLocation(), isCritical, cfg);
            }, cfg.delayTicks);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastDisplayTimes.remove(id);
        damageStacks.keySet().removeIf(k -> k.startsWith(id.toString()));
    }

    private boolean shouldShow(Player damager, Entity target, DamageConfig cfg) {
        if (target instanceof Player && !cfg.pvpEnabled) return false;
        if (!(target instanceof Player) && !cfg.pvmEnabled) return false;
        if (target instanceof LivingEntity living && living.isInvisible() && cfg.ignoreInvisible) return false;
        return !cfg.ignoredTypes.contains(target.getType().name());
    }

    private void stackDamage(Player damager, Entity target, double damage, boolean isCritical, Set<Player> viewers, DamageConfig cfg) {
        long now = System.currentTimeMillis();
        String key = damager.getUniqueId() + ":" + target.getUniqueId();

        // epoch 0 ensures new entries always fall into the "expired" branch first
        DamageStack stack = damageStacks.computeIfAbsent(key, k -> new DamageStack(0, 0, false));

        if (now - stack.lastUpdateTime < cfg.stackWindow) {
            stack.totalDamage += damage;
            stack.lastUpdateTime = now;
            if (isCritical) stack.hasCritical = true;
            if (stack.taskId != -1) plugin.getServer().getScheduler().cancelTask(stack.taskId);
        } else {
            stack.totalDamage = damage;
            stack.lastUpdateTime = now;
            stack.hasCritical = isCritical;
        }

        stack.taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location loc = target.getLocation().add(0, 0.8, 0);
            plugin.getDamageHandler().displayDamageHologram(loc, stack.totalDamage, stack.hasCritical, viewers);
            plugin.getParticleHandler().spawnHitParticles(damager, target.getLocation(), stack.hasCritical);
            playSound(viewers, target.getLocation(), stack.hasCritical, cfg);
            damageStacks.remove(key);
        }, cfg.stackDelay).getTaskId();
    }

    private void playSound(Set<Player> viewers, Location loc, boolean isCritical, DamageConfig cfg) {
        String soundName = isCritical ? cfg.soundCritical : cfg.soundNormal;
        if (soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            viewers.forEach(v -> v.playSound(loc, sound, cfg.soundVolume, cfg.soundPitch));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name: " + soundName);
        }
    }

    private Set<Player> damageViewers(Player damager, Entity target, DamageConfig cfg) {
        if (!"everyone".equals(cfg.visibilityMode)) return Set.of(damager);
        double r2 = cfg.viewRange * cfg.viewRange;
        Set<Player> viewers = new HashSet<>();
        target.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distanceSquared(target.getLocation()) <= r2)
            .forEach(viewers::add);
        return viewers;
    }
}
