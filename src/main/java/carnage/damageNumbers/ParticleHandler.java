package carnage.damageNumbers;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class ParticleHandler {
    private final DamageNumbers plugin;
    private final DamageConfig cfg;

    public ParticleHandler(DamageNumbers plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getDamageConfig();
    }

    public void spawnHitParticles(Player player, Location location, boolean isCritical) {
        spawnParticles(player, location,
            isCritical ? cfg.critParticleType : cfg.normalParticleType,
            isCritical ? cfg.critParticleCount : cfg.normalParticleCount,
            isCritical ? cfg.critParticleOffset : cfg.normalParticleOffset,
            0.5
        );
    }

    public void spawnHealingParticles(Player player, Location location) {
        spawnParticles(player, location, cfg.healParticleType, cfg.healParticleCount, cfg.healParticleOffset, 0.8);
    }

    private void spawnParticles(Player player, Location location, String type, int count, double offset, double yOffset) {
        try {
            player.spawnParticle(Particle.valueOf(type), location.clone().add(0, yOffset, 0), count, offset, offset, offset, 0.0);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type: " + type);
        }
    }
}
