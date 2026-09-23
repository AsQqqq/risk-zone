package ru.riskzones.listener;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.riskzones.RiskZonesPlugin;
import ru.riskzones.zone.ZoneDefinition;

/**
 * Блокирует настроенный список команд, пока игрок находится в чёрной зоне
 * Матчинг - по первому токену команды (без учёта регистра и без учёта
 * префикса плагина вида "essentials:rtp" - сверяем "голое" имя команды)
 */
public final class CommandBlockListener implements Listener {

    private final RiskZonesPlugin plugin;

    public CommandBlockListener(RiskZonesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        ZoneDefinition zone = plugin.getZoneManager().getLastKnownZone(event.getPlayer());
        if (zone == null) return;

        var blocked = zone.getBlockedCommands();
        if (blocked.isEmpty()) return;

        if (event.getPlayer().hasPermission("riskzones.bypass.commands")) {
            return;
        }

        String label = extractLabel(event.getMessage());
        if (!blocked.contains(label)) {
            return;
        }

        event.setCancelled(true);
        String raw = zone.getBlockedCommandMessage().replace("%command%", label);
        event.getPlayer().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }

    private String extractLabel(String rawMessage) {
        // rawMessage начинается с "/", например "/rtp" или "/essentials:rtp arg1"
        String withoutSlash = rawMessage.substring(1);
        int spaceIdx = withoutSlash.indexOf(' ');
        String command = spaceIdx == -1 ? withoutSlash : withoutSlash.substring(0, spaceIdx);

        int colonIdx = command.indexOf(':');
        if (colonIdx != -1) {
            command = command.substring(colonIdx + 1);
        }
        return command.toLowerCase();
    }
}
