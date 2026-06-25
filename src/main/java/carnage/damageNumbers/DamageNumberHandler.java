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
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DamageNumberHandler {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fA-Fk-oK-O])");
    // start high enough to avoid colliding with real server entity IDs
    private static final AtomicInteger entityIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);

    private final DamageNumbers plugin;
    private final DamageConfig cfg;
    private final Random random;
    private final DecimalFormat numberFormat;
    private final Map<UUID, HealStack> healStacks;

    public DamageNumberHandler(DamageNumbers plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getDamageConfig();
        this.random = new Random();
        this.numberFormat = new DecimalFormat("#,##0.#");
        this.healStacks = new ConcurrentHashMap<>();
    }

    private static class HealStack {
        double totalAmount;
        long lastUpdateTime;
        int taskId = -1;

        HealStack(double amount, long time) {
            this.totalAmount = amount;
            this.lastUpdateTime = time;
        }
    }

    public void displayDamageHologram(Location location, double amount, boolean isCritical, Set<Player> viewers) {
        if (viewers.isEmpty()) return;
        Component text = buildText(isCritical ? cfg.criticalFormat : cfg.normalFormat, amount);
        int entityId = entityIdCounter.getAndIncrement();
        Vector3d pos = spawnPos(location);
        viewers.forEach(v -> sendPackets(v, entityId, pos, text));
        scheduleAnimation(viewers, entityId, pos, isCritical ? cfg.lifetimeCritical : cfg.lifetimeNormal, isCritical);
    }

    public void displayHealingHologram(Player healer, Location location, double amount, Set<Player> viewers) {
        if (cfg.stackingHealingEnabled) {
            stackHealing(healer, location, amount, viewers);
        } else {
            showHealing(location, amount, viewers);
        }
    }

    public void spawnTestHologram(Player player, Location location) {
        displayDamageHologram(location, 10.0, false, Set.of(player));
    }

    private void stackHealing(Player healer, Location location, double amount, Set<Player> viewers) {
        long now = System.currentTimeMillis();
        UUID id = healer.getUniqueId();

        // epoch 0 ensures new entries always fall into the "expired" branch first
        HealStack stack = healStacks.computeIfAbsent(id, k -> new HealStack(0, 0));

        if (now - stack.lastUpdateTime < cfg.healStackWindow) {
            stack.totalAmount += amount;
            stack.lastUpdateTime = now;
            if (stack.taskId != -1) plugin.getServer().getScheduler().cancelTask(stack.taskId);
        } else {
            stack.totalAmount = amount;
            stack.lastUpdateTime = now;
        }

        stack.taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            showHealing(location, stack.totalAmount, viewers);
            healStacks.remove(id);
        }, cfg.healStackDelay).getTaskId();
    }

    private void showHealing(Location location, double amount, Set<Player> viewers) {
        Component text = buildText(cfg.healingFormat, amount);
        int entityId = entityIdCounter.getAndIncrement();
        Vector3d pos = spawnPos(location);
        viewers.forEach(v -> sendPackets(v, entityId, pos, text));
        scheduleAnimation(viewers, entityId, pos, cfg.lifetimeHealing, false);
    }

    private Component buildText(String format, double amount) {
        return translateColors(format.replace("%s", numberFormat.format(Math.abs(amount))));
    }

    private Vector3d spawnPos(Location loc) {
        double rand = cfg.randomOffset;
        double x = loc.getX() + cfg.offsetX + (random.nextDouble() - 0.5) * rand;
        double y = loc.getY() + cfg.offsetY;
        double z = loc.getZ() + cfg.offsetZ + (random.nextDouble() - 0.5) * rand;
        return new Vector3d(x, y, z);
    }

    private void sendPackets(Player viewer, int entityId, Vector3d pos, Component text) {
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
            entityId, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY,
            pos, 0.0f, 0.0f, 0.0f, 0, Optional.empty()
        );
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20));
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));
        metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 0x03));
        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(entityId, metadata);

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, meta);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send hologram packets: " + e.getMessage());
        }
    }

    private void scheduleAnimation(Collection<Player> viewers, int entityId, Vector3d pos, int fadeTicks, boolean isCritical) {
        List<Player> snap = List.copyOf(viewers);
        int[] step = {0};

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (step[0] >= fadeTicks) {
                task.cancel();
                WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
                for (Player v : snap) {
                    if (!v.isOnline()) continue;
                    try { PacketEvents.getAPI().getPlayerManager().sendPacket(v, destroy); }
                    catch (Exception e) { if (cfg.debug) plugin.getLogger().fine("Destroy failed: " + e.getMessage()); }
                }
                return;
            }

            double progress = (double) step[0] / fadeTicks;
            double y = pos.getY() + cfg.riseSpeed * (step[0] / 2.0);
            if (cfg.bounceEnabled && progress < 0.3) y += Math.sin(progress * Math.PI * 3) * 0.1;
            double xShake = cfg.shakeOnCrit && isCritical && progress < 0.4 ? (random.nextDouble() - 0.5) * 0.08 : 0;
            double zShake = cfg.shakeOnCrit && isCritical && progress < 0.4 ? (random.nextDouble() - 0.5) * 0.08 : 0;
            double hx = pos.getX() + xShake;
            double hz = pos.getZ() + zShake;
            double fy = y;
            int s = step[0];

            for (Player v : snap) {
                if (!v.isOnline()) continue;
                float yaw = (float) Math.toDegrees(Math.atan2(
                    v.getLocation().getZ() - hz, v.getLocation().getX() - hx
                )) - 90;
                if (cfg.spinEnabled) yaw += s * cfg.spinSpeed * 18.0f;
                WrapperPlayServerEntityTeleport tp = new WrapperPlayServerEntityTeleport(
                    entityId, new Vector3d(hx, fy, hz), yaw, 0.0f, false
                );
                try { PacketEvents.getAPI().getPlayerManager().sendPacket(v, tp); }
                catch (Exception e) { if (cfg.debug) plugin.getLogger().fine("Teleport failed: " + e.getMessage()); }
            }
            step[0] += 2;
        }, 0L, 2L);
    }

    private Component translateColors(String text) {
        if (text == null) return Component.empty();

        StringBuilder buf = new StringBuilder();
        Matcher matcher = COLOR_CODE_PATTERN.matcher(text);
        int lastEnd = 0;
        NamedTextColor color = NamedTextColor.WHITE;
        boolean bold = false;
        Component result = Component.empty();

        while (matcher.find()) {
            buf.append(text, lastEnd, matcher.start());
            if (!buf.isEmpty()) {
                Component part = Component.text(buf.toString(), color);
                if (bold) part = part.decorate(TextDecoration.BOLD);
                result = result.append(part);
                buf.setLength(0);
            }

            char code = matcher.group(1).toLowerCase().charAt(0);
            switch (code) {
                case '0' -> color = NamedTextColor.BLACK;
                case '1' -> color = NamedTextColor.DARK_BLUE;
                case '2' -> color = NamedTextColor.DARK_GREEN;
                case '3' -> color = NamedTextColor.DARK_AQUA;
                case '4' -> color = NamedTextColor.DARK_RED;
                case '5' -> color = NamedTextColor.DARK_PURPLE;
                case '6' -> color = NamedTextColor.GOLD;
                case '7' -> color = NamedTextColor.GRAY;
                case '8' -> color = NamedTextColor.DARK_GRAY;
                case '9' -> color = NamedTextColor.BLUE;
                case 'a' -> color = NamedTextColor.GREEN;
                case 'b' -> color = NamedTextColor.AQUA;
                case 'c' -> color = NamedTextColor.RED;
                case 'd' -> color = NamedTextColor.LIGHT_PURPLE;
                case 'e' -> color = NamedTextColor.YELLOW;
                case 'f' -> color = NamedTextColor.WHITE;
                case 'l' -> bold = true;
                default -> {
                    buf.append('&').append(code);
                    continue;
                }
            }
            lastEnd = matcher.end();
        }

        buf.append(text.substring(lastEnd));
        if (!buf.isEmpty()) {
            Component part = Component.text(buf.toString(), color);
            if (bold) part = part.decorate(TextDecoration.BOLD);
            result = result.append(part);
        }

        return result;
    }
}
