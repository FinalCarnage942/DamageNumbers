package carnage.damageNumbers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.naming.Name;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the display of damage and healing holograms using PacketEvents.
 */
public class DamageNumberHandler {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fA-Fk-oK-O])");
    private static final String CONFIG_NORMAL_FORMAT = "formats.normal";
    private static final String CONFIG_CRITICAL_FORMAT = "formats.critical";
    private static final String CONFIG_HEALING_FORMAT = "formats.healing";
    private static final String CONFIG_OFFSET_X = "display.offset.x";
    private static final String CONFIG_OFFSET_Y = "display.offset.y";
    private static final String CONFIG_OFFSET_Z = "display.offset.z";
    private static final String CONFIG_RANDOM_OFFSET = "display.random-offset";
    private static final String CONFIG_LIFETIME_NORMAL = "advanced.lifetime.normal";
    private static final String CONFIG_LIFETIME_CRITICAL = "advanced.lifetime.critical";
    private static final String CONFIG_LIFETIME_HEALING = "advanced.lifetime.healing";
    private static final String CONFIG_STACKING_HEALING = "advanced.stacking.healing-enabled";
    private static final String CONFIG_STACK_WINDOW = "healing.stack-window-ms";
    private static final String CONFIG_STACK_DELAY = "healing.stack-delay-ticks";

    private final DamageNumbers plugin;
    private final Map<String, String> damageFormats;
    private final Random random;
    private final DecimalFormat numberFormat;
    private final Map<UUID, HealStack> healStacks;

    public DamageNumberHandler(DamageNumbers plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.numberFormat = new DecimalFormat("#,##0.#");
        this.damageFormats = loadDamageFormats();
        this.healStacks = new ConcurrentHashMap<>();
        plugin.getPluginLogger().info("DamageNumberHandler initialized with PacketEvents text display holograms");
    }

    /**
     * Inner class to manage stacked healing data.
     */
    private static class HealStack {
        private double totalAmount;
        private long lastUpdateTime;
        private int taskId;

        HealStack(double amount, long time) {
            this.totalAmount = amount;
            this.lastUpdateTime = time;
            this.taskId = -1;
        }
    }

    /**
     * Loads damage format strings from the configuration.
     *
     * @return a map of format types to their format strings
     */
    private Map<String, String> loadDamageFormats() {
        Map<String, String> formats = new HashMap<>();
        FileConfiguration config = plugin.getConfig();
        formats.put("normal", config.getString(CONFIG_NORMAL_FORMAT, "&7%s"));
        formats.put("critical", config.getString(CONFIG_CRITICAL_FORMAT, "&6&l%s ✧"));
        formats.put("healing", config.getString(CONFIG_HEALING_FORMAT, "&a+%s ❤"));
        plugin.getPluginLogger().info("Loaded damage formats: " + formats);
        return formats;
    }

    /**
     * Translates legacy color codes (&x) to Adventure Component with NamedTextColor.
     *
     * @param text the text with legacy color codes
     * @return the formatted Component
     */
    private Component translateLegacyColorCodes(String text) {
        if (text == null) {
            return Component.empty();
        }

        StringBuilder builder = new StringBuilder();
        Matcher matcher = COLOR_CODE_PATTERN.matcher(text);
        int lastEnd = 0;
        NamedTextColor currentColor = NamedTextColor.WHITE;
        boolean bold = false;
        Component result = Component.empty();

        while (matcher.find()) {
            builder.append(text, lastEnd, matcher.start());
            if (!builder.isEmpty()) {
                Component part = Component.text(builder.toString(), currentColor);
                if (bold) {
                    part = part.decorate(TextDecoration.BOLD);
                }
                result = result.append(part);
                builder.setLength(0);
            }

            char code = matcher.group(1).toLowerCase().charAt(0);
            switch (code) {
                case '0' -> currentColor = NamedTextColor.BLACK;
                case '1' -> currentColor = NamedTextColor.DARK_BLUE;
                case '2' -> currentColor = NamedTextColor.DARK_GREEN;
                case '3' -> currentColor = NamedTextColor.DARK_AQUA;
                case '4' -> currentColor = NamedTextColor.DARK_RED;
                case '5' -> currentColor = NamedTextColor.DARK_PURPLE;
                case '6' -> currentColor = NamedTextColor.GOLD;
                case '7' -> currentColor = NamedTextColor.GRAY;
                case '8' -> currentColor = NamedTextColor.DARK_GRAY;
                case '9' -> currentColor = NamedTextColor.BLUE;
                case 'a' -> currentColor = NamedTextColor.GREEN;
                case 'b' -> currentColor = NamedTextColor.AQUA;
                case 'c' -> currentColor = NamedTextColor.RED;
                case 'd' -> currentColor = NamedTextColor.LIGHT_PURPLE;
                case 'e' -> currentColor = NamedTextColor.YELLOW;
                case 'f' -> currentColor = NamedTextColor.WHITE;
                case 'l' -> bold = true;
                default -> {
                    builder.append('&').append(code);
                    continue;
                }
            }
            lastEnd = matcher.end();
        }

        builder.append(text.substring(lastEnd));
        if (!builder.isEmpty()) {
            Component part = Component.text(builder.toString(), currentColor);
            if (bold) {
                part = part.decorate(TextDecoration.BOLD);
            }
            result = result.append(part);
        }

        return result;
    }

    /**
     * Displays a damage hologram for a hit.
     *
     * @param viewer     the player viewing the hologram
     * @param location   the location to display the hologram
     * @param amount     the damage amount
     * @param isCritical whether the hit is critical
     * @param viewers    the set of players who should see the hologram
     */
    public void displayDamageHologram(Player viewer, Location location, double amount, boolean isCritical, Set<Player> viewers) {
        if (!viewers.contains(viewer)) {
            return;
        }

        Component textComponent = createHologramText(amount, isCritical);
        int entityId = generateEntityId();
        Vector3d spawnPosition = getSpawnPosition(location);
        sendHologramPackets(viewer, entityId, spawnPosition, textComponent);
        scheduleHologramAnimation(viewer, entityId, location, spawnPosition, isCritical);
    }

    /**
     * Displays a healing hologram.
     *
     * @param healer   the healed player
     * @param location the location to display the hologram
     * @param amount   the healing amount
     * @param viewers  the set of players who should see the hologram
     */
    public void displayHealingHologram(Player healer, Location location, double amount, Set<Player> viewers) {
        if (plugin.getConfig().getBoolean(CONFIG_STACKING_HEALING, true)) {
            handleStackedHealing(healer, location, amount, viewers);
        } else {
            showHealingHologram(healer, location, amount, viewers);
        }
    }

    /**
     * Creates the text component for a hologram.
     *
     * @param amount     the damage or healing amount
     * @param isCritical whether the hit is critical
     * @return the formatted Component
     */
    private Component createHologramText(double amount, boolean isCritical) {
        String formatKey = isCritical ? "critical" : "normal";
        String format = damageFormats.getOrDefault(formatKey, "&7%s");
        String amountText = numberFormat.format(Math.abs(amount));
        String legacyText = format.replace("%s", amountText);
        return translateLegacyColorCodes(legacyText);
    }

    /**
     * Generates a unique entity ID for the hologram.
     *
     * @return the entity ID
     */
    private int generateEntityId() {
        return random.nextInt(Integer.MAX_VALUE - 100000) + 100000;
    }

    /**
     * Gets the spawn position for the hologram with random offsets.
     *
     * @param location the base location
     * @return the spawn position
     */
    private Vector3d getSpawnPosition(Location location) {
        FileConfiguration config = plugin.getConfig();
        double offsetX = config.getDouble(CONFIG_OFFSET_X, 0.0) + (random.nextDouble() - 0.5) * config.getDouble(CONFIG_RANDOM_OFFSET, 0.4);
        double offsetY = config.getDouble(CONFIG_OFFSET_Y, 0.8);
        double offsetZ = config.getDouble(CONFIG_OFFSET_Z, 0.0) + (random.nextDouble() - 0.5) * config.getDouble(CONFIG_RANDOM_OFFSET, 0.4);
        return new Vector3d(location.getX() + offsetX, location.getY() + offsetY, location.getZ() + offsetZ);
    }

    /**
     * Sends hologram spawn and metadata packets to the viewer.
     *
     * @param viewer     the player viewing the hologram
     * @param entityId   the entity ID
     * @param position   the spawn position
     * @param textComponent the hologram text
     */
    private void sendHologramPackets(Player viewer, int entityId, Vector3d position, Component textComponent) {
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY, position, 0.0f, 0.0f, 0.0f, 0, Optional.empty()
        );
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20));
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, textComponent));
        metadata.add(new EntityData<>(25, EntityDataTypes.INT, 0x40000000));
        metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 0x03));
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            if (debug) {
                plugin.getPluginLogger().info("Sent hologram to " + viewer.getName() + ": " + textComponent + " (EntityID: " + entityId + ")");
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send packets: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Schedules the animation and destruction of the hologram.
     *
     * @param viewer     the player viewing the hologram
     * @param entityId   the entity ID
     * @param location   the base location
     * @param position   the initial spawn position
     * @param isCritical whether the hit is critical
     */
    private void scheduleHologramAnimation(Player viewer, int entityId, Location location, Vector3d position, boolean isCritical) {
        FileConfiguration config = plugin.getConfig();
        int fadeTicks = isCritical ? config.getInt(CONFIG_LIFETIME_CRITICAL, 40) : config.getInt(CONFIG_LIFETIME_NORMAL, 40);
        double riseSpeed = config.getDouble("animation.rise-speed", 0.05);
        boolean bounceEnabled = config.getBoolean("animation.bounce", true);
        boolean shakeEnabled = config.getBoolean("animation.shake-on-crit", true) && isCritical;
        boolean spinEnabled = config.getDouble("animation.spin-speed", 0.0) > 0;
        double spinSpeed = config.getDouble("animation.spin-speed", 0.0);
        boolean debug = config.getBoolean("debug", false);

        for (int step = 0; step < fadeTicks; step += 2) {
            final int currentStep = step;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }

                double progress = (double) currentStep / fadeTicks;
                double yOffset = position.getY() + riseSpeed * (currentStep / 2.0);

                if (bounceEnabled && progress < 0.3) {
                    yOffset += Math.sin(progress * Math.PI * 3) * 0.1;
                }

                double xShake = shakeEnabled && progress < 0.4 ? (random.nextDouble() - 0.5) * 0.08 : 0;
                double zShake = shakeEnabled && progress < 0.4 ? (random.nextDouble() - 0.5) * 0.08 : 0;
                float yaw = spinEnabled ? (float) (currentStep * spinSpeed * 18.0) : 0.0f;

                WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                        entityId, new Vector3d(position.getX() + xShake, yOffset, position.getZ() + zShake), yaw, 0.0f, false
                );

                try {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
                } catch (Exception e) {
                    if (debug) {
                        plugin.getPluginLogger().fine("Teleport failed: " + e.getMessage());
                    }
                }
            }, step);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                if (debug) {
                    plugin.getPluginLogger().fine("Destroyed hologram: " + entityId);
                }
            } catch (Exception e) {
                if (debug) {
                    plugin.getPluginLogger().fine("Destroy failed: " + e.getMessage());
                }
            }
        }, fadeTicks);
    }

    /**
     * Handles stacked healing for delayed display.
     *
     * @param healer   the healed player
     * @param location the location to display the hologram
     * @param amount   the healing amount
     * @param viewers  the set of players who should see the hologram
     */
    private void handleStackedHealing(Player healer, Location location, double amount, Set<Player> viewers) {
        long currentTime = System.currentTimeMillis();
        UUID healerId = healer.getUniqueId();
        long stackWindow = plugin.getConfig().getLong(CONFIG_STACK_WINDOW, 500);

        HealStack stack = healStacks.computeIfAbsent(healerId, k -> new HealStack(amount, currentTime));

        if (currentTime - stack.lastUpdateTime < stackWindow) {
            stack.totalAmount += amount;
            stack.lastUpdateTime = currentTime;

            if (stack.taskId != -1) {
                plugin.getServer().getScheduler().cancelTask(stack.taskId);
            }
        } else {
            stack.totalAmount = amount;
            stack.lastUpdateTime = currentTime;
        }

        stack.taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            showHealingHologram(healer, location, stack.totalAmount, viewers);
            healStacks.remove(healerId);
        }, plugin.getConfig().getInt(CONFIG_STACK_DELAY, 10)).getTaskId();
    }

    /**
     * Shows a healing hologram to viewers.
     *
     * @param healer   the healed player
     * @param location the location to display the hologram
     * @param amount   the healing amount
     * @param viewers  the set of players who should see the hologram
     */
    private void showHealingHologram(Player healer, Location location, double amount, Set<Player> viewers) {
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        String format = damageFormats.getOrDefault("healing", "&a+%s ❤");
        String amountText = numberFormat.format(amount);
        Component textComponent = translateLegacyColorCodes(format.replace("%s", amountText));
        int entityId = generateEntityId();
        Vector3d spawnPosition = getSpawnPosition(location);

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY, spawnPosition, 0.0f, 0.0f, 0.0f, 0, Optional.empty()
        );
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20));
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, textComponent));
        metadata.add(new EntityData<>(25, EntityDataTypes.INT, 0x40000000));
        metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 0x03));
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

        viewers.forEach(viewer -> {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
                if (debug) {
                    plugin.getPluginLogger().info("Sent healing hologram to " + viewer.getName() + ": " + textComponent + " (EntityID: " + entityId + ")");
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Failed to send packets: " + e.getMessage());
                if (debug) {
                    e.printStackTrace();
                }
            }
        });

        scheduleHologramAnimation(viewers.iterator().next(), entityId, location, spawnPosition, false);
    }

    /**
     * Spawns a test hologram for debugging.
     *
     * @param player   the player to see the hologram
     * @param location the location to display the hologram
     */
    public void spawnTestHologram(Player player, Location location) {
        Set<Player> viewers = new HashSet<>();
        viewers.add(player);
        displayDamageHologram(player, location, 10.0, false, viewers);
        plugin.getPluginLogger().info("Spawned test hologram for " + player.getName() + " at " + location);
    }
}