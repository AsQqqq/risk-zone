package ru.riskzones.zone;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Настройки зон для одного мира - список произвольных колец вокруг общего
 * центра (shape/center-x/center-z общие на мир, зоны - концентрические
 * кольца вокруг него). Список отсортирован по возрастанию from
 */
public final class WorldZoneConfig {

    private final boolean enabled;
    private final double centerX;
    private final double centerZ;
    private final ZoneShape shape;
    private final List<ZoneDefinition> zones;

    private WorldZoneConfig(boolean enabled, double centerX, double centerZ, ZoneShape shape,
                             List<ZoneDefinition> zones) {
        this.enabled = enabled;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.shape = shape;
        this.zones = zones;
    }

    public static WorldZoneConfig loadFrom(ConfigurationSection worldSection, String worldName, Logger logger) {
        boolean enabled = worldSection.getBoolean("enabled", true);
        double centerX = worldSection.getDouble("center-x", 0);
        double centerZ = worldSection.getDouble("center-z", 0);
        ZoneShape shape = ZoneShape.parse(worldSection.getString("shape"), ZoneShape.CIRCLE);

        List<ZoneDefinition> zones = new ArrayList<>();
        ConfigurationSection zonesSection = worldSection.getConfigurationSection("zones");
        if (zonesSection != null) {
            for (String zoneId : zonesSection.getKeys(false)) {
                ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
                if (zoneSection == null) continue;
                zones.add(ZoneDefinitionLoader.load(zoneId, zoneSection, logger));
            }
        }
        zones.sort(Comparator.comparingDouble(ZoneDefinition::getFrom));

        if (!validate(zones, worldName, logger)) {
            zones = List.of();
        }

        return new WorldZoneConfig(enabled, centerX, centerZ, shape, zones);
    }

    /**
     * Проверяет список зон (уже отсортированный по from): to > from (кроме
     * -1 = бесконечно), бесконечной может быть только последняя по from
     * зона (иначе "из неё можно попасть в другую зону с + координаты" -
     * ровно то, что просили не допускать), соседние зоны не пересекаются
     * (разрывы - можно). При ошибке - severe в лог и зоны мира отключаются
     * целиком (безопасный отказ вместо падения плагина)
     */
    private static boolean validate(List<ZoneDefinition> zones, String worldName, Logger logger) {
        for (int i = 0; i < zones.size(); i++) {
            ZoneDefinition zone = zones.get(i);
            boolean isLast = i == zones.size() - 1;

            if (zone.getTo() >= 0 && zone.getTo() <= zone.getFrom()) {
                logger.severe("RiskZones: мир '" + worldName + "', зона '" + zone.getId() + "' - to ("
                        + zone.getTo() + ") должно быть больше from (" + zone.getFrom()
                        + ") или -1 (бесконечно). Зоны для этого мира отключены.");
                return false;
            }

            if (zone.getTo() < 0 && !isLast) {
                logger.severe("RiskZones: мир '" + worldName + "', зона '" + zone.getId()
                        + "' задана бесконечной (to: -1), но не самая дальняя - за ней есть зона '"
                        + zones.get(i + 1).getId() + "' (from " + zones.get(i + 1).getFrom() + "). "
                        + "Бесконечной (to: -1) может быть только зона с максимальным from в мире. "
                        + "Зоны для этого мира отключены.");
                return false;
            }

            if (!isLast) {
                ZoneDefinition next = zones.get(i + 1);
                if (zone.getTo() > next.getFrom()) {
                    logger.severe("RiskZones: мир '" + worldName + "', зоны '" + zone.getId() + "' (до "
                            + zone.getTo() + ") и '" + next.getId() + "' (от " + next.getFrom()
                            + ") пересекаются. Зоны для этого мира отключены.");
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double distance(double x, double z) {
        return switch (shape) {
            case CIRCLE -> Math.hypot(x - centerX, z - centerZ);
            case SQUARE -> Math.max(Math.abs(x - centerX), Math.abs(z - centerZ));
        };
    }

    /** Зона в этой точке, или null - точка вне всех настроенных зон. */
    public ZoneDefinition resolve(double x, double z) {
        if (!enabled) return null;
        double dist = distance(x, z);
        for (ZoneDefinition zone : zones) {
            if (zone.containsDistance(dist)) return zone;
        }
        return null;
    }

    /** Ближайшая по radius зона, которую игрок ещё не достиг (для approach-warning), или null */
    public ZoneDefinition nextZone(double x, double z) {
        if (!enabled) return null;
        double dist = distance(x, z);
        for (ZoneDefinition zone : zones) {
            if (dist < zone.getFrom()) return zone;
        }
        return null;
    }

    public double distanceToZoneEdge(double x, double z, ZoneDefinition zone) {
        return zone.getFrom() - distance(x, z);
    }

    public List<ZoneDefinition> getZones() {
        return zones;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public ZoneShape getShape() {
        return shape;
    }
}
