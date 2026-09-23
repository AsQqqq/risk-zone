package ru.riskzones.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.riskzones.RiskZonesPlugin;
import ru.riskzones.zone.ZoneDefinition;

/**
 * Публичный статический API для других плагинов сервера (например, того,
 * что выдаёт донат/ролевые бафы). Добавь riskzones-plugin.jar в classpath
 * как provided-зависимость и дёргай эти методы, либо слушай
 * {@link ru.riskzones.events.RiskZoneChangeEvent} - что удобнее
 *
 * Зоны кастомные и задаются в config.yml - у каждой свой id (например
 * "green"/"black" в конфиге по умолчанию, но админ может назвать и
 * настроить их как угодно)
 *
 * Пример использования из другого плагина:
 * <pre>
 *   if (RiskZonesAPI.isInZone(player, "black")) {
 *       // не выдавать/снять бафф
 *   }
 * </pre>
 */
public final class RiskZonesAPI {

    private RiskZonesAPI() {
    }

    /** null = игрок вне всех настроенных зон. */
    public static ZoneDefinition getZone(Player player) {
        return RiskZonesPlugin.getInstance().getZoneManager().getLastKnownZone(player);
    }

    public static ZoneDefinition getZone(Location location) {
        return RiskZonesPlugin.getInstance().getZoneManager().resolve(location);
    }

    public static boolean isInZone(Player player, String zoneId) {
        ZoneDefinition zone = getZone(player);
        return zone != null && zone.getId().equals(zoneId);
    }

    public static boolean isInAnyZone(Player player) {
        return getZone(player) != null;
    }
}
