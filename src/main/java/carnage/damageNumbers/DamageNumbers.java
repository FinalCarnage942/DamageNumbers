package carnage.damageNumbers;

import carnage.damageNumbers.commands.ReloadCommand;
import carnage.damageNumbers.commands.TestCommand;
import carnage.damageNumbers.listeners.DamageListener;
import carnage.damageNumbers.listeners.PacketListener;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public class DamageNumbers extends JavaPlugin {
    private DamageConfig damageConfig;
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
        saveDefaultConfig();
        PacketEvents.getAPI().init();
        this.damageConfig = new DamageConfig(getConfig());
        this.damageHandler = new DamageNumberHandler(this);
        this.particleHandler = new ParticleHandler(this);

        new DamageListener(this);
        new PacketListener(this);
        getCommand("dnreload").setExecutor(new ReloadCommand(this));
        getCommand("damagenumbers").setExecutor(new TestCommand(this));

        getLogger().info("DamageNumbers enabled");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
    }

    public void reload() {
        reloadConfig();
        this.damageConfig = new DamageConfig(getConfig());
        this.damageHandler = new DamageNumberHandler(this);
        this.particleHandler = new ParticleHandler(this);
    }

    public DamageConfig getDamageConfig() { return damageConfig; }
    public DamageNumberHandler getDamageHandler() { return damageHandler; }
    public ParticleHandler getParticleHandler() { return particleHandler; }
}
