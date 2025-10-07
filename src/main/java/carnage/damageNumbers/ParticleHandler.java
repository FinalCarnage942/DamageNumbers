package carnage.damageNumbers;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Manages particle effects for damage and healing events.
 */
public class ParticleHandler {
    private static final String CONFIG_CRITICAL_TYPE = "particles.critical.type";
    private static final String CONFIG_CRITICAL_COUNT = "particles.critical.count";
    private static final String CONFIG_CRITICAL_OFFSET = "particles.critical.offset";
    private static final String CONFIG_NORMAL_TYPE = "particles.normal.type";
    private static final String CONFIG_NORMAL_COUNT = "particles.normal.count";
    private static final String CONFIG_NORMAL_OFFSET = "particles.normal.offset";
    private static final String CONFIG_HEALING_TYPE = "particles.healing.type";
    private static final String CONFIG_HEALING_COUNT = "particles.healing.count";
    private static final String CONFIG_HEALING_OFFSET = "particles.healing.offset";

    private final DamageNumbers plugin;

    public ParticleHandler(DamageNumbers plugin) {
        this.plugin = plugin;
        plugin.getPluginLogger().info("ParticleHandler initialized");
    }

    /**
     * Spawns particles for a hit event.
     *
     * @param player     the player to see the particles
     * @param location   the location to spawn particles
     * @param isCritical whether the hit is critical
     */
    public void spawnHitParticles(Player player, Location location, boolean isCritical) {
        FileConfiguration config = plugin.getConfig();
        String particleType = isCritical ? config.getString(CONFIG_CRITICAL_TYPE, "CRIT") : config.getString(CONFIG_NORMAL_TYPE, "DAMAGE_INDICATOR");
        int count = isCritical ? config.getInt(CONFIG_CRITICAL_COUNT, 10) : config.getInt(CONFIG_NORMAL_COUNT, 5);
        double offset = isCritical ? config.getDouble(CONFIG_CRITICAL_OFFSET, 0.4) : config.getDouble(CONFIG_NORMAL_OFFSET, 0.3);

        spawnParticles(player, location, particleType, count, offset, 0.5);
    }

    /**
     * Spawns particles for a healing event.
     *
     * @param player   the player to see the particles
     * @param location the location to spawn particles
     */
    public void spawnHealingParticles(Player player, Location location) {
        FileConfiguration config = plugin.getConfig();
        String particleType = config.getString(CONFIG_HEALING_TYPE, "HEART");
        int count = config.getInt(CONFIG_HEALING_COUNT, 3);
        double offset = config.getDouble(CONFIG_HEALING_OFFSET, 0.2);

        spawnParticles(player, location, particleType, count, offset, 0.8);
    }

    /**
     * Spawns particles at the specified location.
     *
     * @param player      the player to see the particles
     * @param location    the base location
     * @param particleType the type of particle
     * @param count       the number of particles
     * @param offset      the offset for particle spread
     * @param yOffset     the vertical offset
     */
    private void spawnParticles(Player player, Location location, String particleType, int count, double offset, double yOffset) {
        try {
            Particle particle = Particle.valueOf(particleType);
            player.spawnParticle(particle, location.clone().add(0, yOffset, 0), count, offset, offset, offset, 0.0);
            plugin.getPluginLogger().fine("Spawned " + particleType + " particles for " + player.getName() + " at " + location);
        } catch (IllegalArgumentException e) {
            plugin.getPluginLogger().warning("Invalid particle type: " + particleType);
        }
    }
}