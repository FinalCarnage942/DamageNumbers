package carnage.damageNumbers;

import carnage.damageNumbers.commands.ReloadCommand;
import carnage.damageNumbers.commands.TestCommand;
import carnage.damageNumbers.listeners.DamageListener;
import carnage.damageNumbers.listeners.PacketListener;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Main plugin class for DamageNumbers, managing initialization and component access.
 */
public class DamageNumbers extends JavaPlugin {
    private Logger pluginLogger;
    private DamageNumberHandler damageHandler;
    private ParticleHandler particleHandler;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        initializeComponents();
        registerComponents();
        pluginLogger.info("DamageNumbers enabled successfully!");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        pluginLogger.info("DamageNumbers disabled");
    }

    /**
     * Initializes plugin components.
     */
    private void initializeComponents() {
        this.pluginLogger = getLogger();
        saveDefaultConfig();
        PacketEvents.getAPI().init();
        this.damageHandler = new DamageNumberHandler(this);
        this.particleHandler = new ParticleHandler(this);
    }

    /**
     * Registers event listeners and commands.
     */
    private void registerComponents() {
        new DamageListener(this, damageHandler, particleHandler);
        new PacketListener(this, damageHandler, particleHandler);
        getCommand("dnreload").setExecutor(new ReloadCommand(this));
        getCommand("damagenumbers").setExecutor(new TestCommand(this, damageHandler, particleHandler));
    }

    /**
     * Gets the plugin logger.
     *
     * @return the logger instance
     */
    public Logger getPluginLogger() {
        return pluginLogger;
    }

    /**
     * Reloads the damage and particle handlers.
     */
    public void reloadHandler() {
        this.damageHandler = new DamageNumberHandler(this);
        this.particleHandler = new ParticleHandler(this);
        pluginLogger.info("DamageNumberHandler and ParticleHandler reloaded");
    }
}