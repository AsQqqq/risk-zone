package ru.riskzones.zone;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Хранит конфиг зон по мирам, текущую зону каждого игрока (null = вне
 * всех настроенных зон) и его "исходное" состояние allow-flight (чтобы
 * честно вернуть его при выходе из зоны с disable-flight, независимо от
 * того, чем оно было выдано - правом, другим плагином и т.д.), а также
 * состояние приближения к следующей зоне (для approach-warning)
 */
public final class ZoneManager {

    private final Map<String, WorldZoneConfig> worldConfigs = new HashMap<>();
    private final Map<UUID, ZoneDefinition> currentZone = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cachedAllowFlight = new ConcurrentHashMap<>();
    private final Map<UUID, String> approachingZoneId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastZoneWarningAtMillis = new ConcurrentHashMap<>();

    public void loadFrom(ConfigurationSection worldsSection, Logger logger) {
        worldConfigs.clear();
        if (worldsSection == null) return;

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection sec = worldsSection.getConfigurationSection(worldName);
            if (sec == null) continue;
            worldConfigs.put(worldName.toLowerCase(), WorldZoneConfig.loadFrom(sec, worldName, logger));
        }
    }

    public WorldZoneConfig configFor(World world) {
        if (world == null) return null;
        return worldConfigs.get(world.getName().toLowerCase());
    }

    /** Зона в этой точке, или null - вне всех настроенных зон ("нет зоны", аналог старого SAFE) */
    public ZoneDefinition resolve(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        WorldZoneConfig cfg = configFor(loc.getWorld());
        if (cfg == null) return null;
        return cfg.resolve(loc.getX(), loc.getZ());
    }

    /** Ближайшая ещё не достигнутая зона (для approach-warning), или null */
    public ZoneDefinition nextZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        WorldZoneConfig cfg = configFor(loc.getWorld());
        if (cfg == null) return null;
        return cfg.nextZone(loc.getX(), loc.getZ());
    }

    public double distanceToZoneEdge(Location loc, ZoneDefinition zone) {
        if (loc == null || loc.getWorld() == null) return Double.MAX_VALUE;
        WorldZoneConfig cfg = configFor(loc.getWorld());
        if (cfg == null) return Double.MAX_VALUE;
        return cfg.distanceToZoneEdge(loc.getX(), loc.getZ(), zone);
    }

    /** null = игрок вне всех настроенных зон */
    public ZoneDefinition getLastKnownZone(Player player) {
        return currentZone.get(player.getUniqueId());
    }

    public void setLastKnownZone(Player player, ZoneDefinition zone) {
        if (zone == null) {
            currentZone.remove(player.getUniqueId());
        } else {
            currentZone.put(player.getUniqueId(), zone);
        }
    }

    public void forgetPlayer(Player player) {
        currentZone.remove(player.getUniqueId());
        cachedAllowFlight.remove(player.getUniqueId());
        approachingZoneId.remove(player.getUniqueId());
        lastZoneWarningAtMillis.remove(player.getUniqueId());
    }

    /** Запоминает allow-flight игрока ДО того, как плагин его выключит */
    public void cacheAllowFlight(Player player) {
        cachedAllowFlight.putIfAbsent(player.getUniqueId(), player.getAllowFlight());
    }

    /** Возвращает запомненное allow-flight (или null, если ничего не кэшировано) */
    public Boolean popCachedAllowFlight(Player player) {
        return cachedAllowFlight.remove(player.getUniqueId());
    }

    public boolean hasWorldConfigured(World world) {
        return configFor(world) != null && configFor(world).isEnabled();
    }

    /** Id зоны, к границе которой игрок сейчас приближается (для throttling предупреждений), или null */
    public String getApproachingZoneId(Player player) {
        return approachingZoneId.get(player.getUniqueId());
    }

    public void setApproachingZoneId(Player player, String zoneId) {
        if (zoneId == null) {
            approachingZoneId.remove(player.getUniqueId());
        } else {
            approachingZoneId.put(player.getUniqueId(), zoneId);
        }
    }

    public long getLastZoneWarningAtMillis(Player player) {
        return lastZoneWarningAtMillis.getOrDefault(player.getUniqueId(), 0L);
    }

    public void markZoneWarnedNow(Player player) {
        lastZoneWarningAtMillis.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
