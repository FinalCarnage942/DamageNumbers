package carnage.damageNumbers;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class DamageConfig {
    public final String normalFormat, criticalFormat, healingFormat;

    public final double offsetX, offsetY, offsetZ, randomOffset;

    public final int lifetimeNormal, lifetimeCritical, lifetimeHealing;

    public final boolean stackingEnabled, stackingHealingEnabled;
    public final long stackWindow, healStackWindow;
    public final int stackDelay, healStackDelay;

    public final long cooldownMs, healCooldownMs;
    public final int delayTicks, healDelayTicks;

    public final String visibilityMode, healVisibilityMode;
    public final double viewRange, healViewRange;

    public final boolean pvpEnabled, pvmEnabled, mvpEnabled, ignoreInvisible;
    public final List<String> ignoredTypes;

    public final String soundNormal, soundCritical, soundHealing;
    public final float soundVolume, soundPitch;

    public final double riseSpeed, spinSpeed;
    public final boolean bounceEnabled, shakeOnCrit, spinEnabled;

    public final String critParticleType, normalParticleType, healParticleType;
    public final int critParticleCount, normalParticleCount, healParticleCount;
    public final double critParticleOffset, normalParticleOffset, healParticleOffset;

    public final boolean debug;

    public DamageConfig(FileConfiguration cfg) {
        normalFormat = cfg.getString("formats.normal", "&7%s");
        criticalFormat = cfg.getString("formats.critical", "&6&l%s ✧");
        healingFormat = cfg.getString("formats.healing", "&a+%s ❤");

        offsetX = cfg.getDouble("display.offset.x", 0.0);
        offsetY = cfg.getDouble("display.offset.y", 0.8);
        offsetZ = cfg.getDouble("display.offset.z", 0.0);
        randomOffset = cfg.getDouble("display.random-offset", 0.4);

        lifetimeNormal = cfg.getInt("advanced.lifetime.normal", 40);
        lifetimeCritical = cfg.getInt("advanced.lifetime.critical", 40);
        lifetimeHealing = cfg.getInt("advanced.lifetime.healing", 40);

        stackingEnabled = cfg.getBoolean("advanced.stacking.enabled", false);
        stackingHealingEnabled = cfg.getBoolean("advanced.stacking.healing-enabled", true);
        stackWindow = cfg.getLong("advanced.stacking.window-ms", 300);
        healStackWindow = cfg.getLong("healing.stack-window-ms", 500);
        stackDelay = cfg.getInt("advanced.stacking.delay-ticks", 5);
        healStackDelay = cfg.getInt("healing.stack-delay-ticks", 10);

        cooldownMs = cfg.getLong("cooldown-ms", 50);
        healCooldownMs = cfg.getLong("healing.cooldown-ms", 50);
        delayTicks = cfg.getInt("advanced.delay-ticks", 0);
        healDelayTicks = cfg.getInt("advanced.healing-delay-ticks", 0);

        visibilityMode = cfg.getString("display.visibility", "damager").toLowerCase();
        healVisibilityMode = cfg.getString("healing.visibility", "healer").toLowerCase();
        viewRange = cfg.getDouble("display.view-range", 32.0);
        healViewRange = cfg.getDouble("healing.view-range", 32.0);

        pvpEnabled = cfg.getBoolean("triggers.player-vs-player", true);
        pvmEnabled = cfg.getBoolean("triggers.player-vs-mob", true);
        mvpEnabled = cfg.getBoolean("triggers.mob-vs-player", false);
        ignoreInvisible = cfg.getBoolean("triggers.ignore-invisible", true);
        ignoredTypes = cfg.getStringList("triggers.ignored-entity-types");

        soundNormal = cfg.getString("advanced.sounds.normal", "");
        soundCritical = cfg.getString("advanced.sounds.critical", "");
        soundHealing = cfg.getString("advanced.sounds.healing", "");
        soundVolume = (float) cfg.getDouble("advanced.sounds.volume", 0.5);
        soundPitch = (float) cfg.getDouble("advanced.sounds.pitch", 1.0);

        riseSpeed = cfg.getDouble("animation.rise-speed", 0.05);
        bounceEnabled = cfg.getBoolean("animation.bounce", true);
        shakeOnCrit = cfg.getBoolean("animation.shake-on-crit", true);
        spinSpeed = cfg.getDouble("animation.spin-speed", 0.0);
        spinEnabled = spinSpeed > 0;

        critParticleType = cfg.getString("particles.critical.type", "CRIT");
        normalParticleType = cfg.getString("particles.normal.type", "DAMAGE_INDICATOR");
        healParticleType = cfg.getString("particles.healing.type", "HEART");
        critParticleCount = cfg.getInt("particles.critical.count", 10);
        normalParticleCount = cfg.getInt("particles.normal.count", 5);
        healParticleCount = cfg.getInt("particles.healing.count", 3);
        critParticleOffset = cfg.getDouble("particles.critical.offset", 0.4);
        normalParticleOffset = cfg.getDouble("particles.normal.offset", 0.3);
        healParticleOffset = cfg.getDouble("particles.healing.offset", 0.2);

        debug = cfg.getBoolean("debug", false);
    }
}
