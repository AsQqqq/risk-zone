package ru.riskzones.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import ru.riskzones.RiskZonesPlugin;

/**
 * Отслеживает перемещения игрока и просит ZoneUpdateService пересчитать
 * его зону. Сам не содержит бизнес-логики - вся она в ZoneUpdateService,
 * чтобы подстраховочный таймер в главном классе плагина мог дёргать её
 * напрямую, без фейковых Bukkit-событий
 */
public final class MovementListener implements Listener {

    private final RiskZonesPlugin plugin;

    public MovementListener(RiskZonesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Пересчитываем только если игрок реально сменил блок X/Z - повороты
        // головы/прыжки на месте не должны гонять расчёты каждый тик
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        plugin.getZoneUpdateService().update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // На момент этого события координаты уже целевые, но на всякий случай
        // откладываем на тик - некоторые сторонние плагины донастраивают
        // позицию игрока постфактум (например, safe-teleport)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getZoneUpdateService().update(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getZoneUpdateService().update(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getZoneUpdateService().update(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getZoneUpdateService().update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Если игрок вышел прямо в чёрной зоне с зажатым полётом - вернём
        // исходное состояние, чтобы не остаться в кэше навсегда, и забудем игрока
        Player player = event.getPlayer();
        plugin.getZoneUpdateService().restoreFlightIfCached(player);
        plugin.getZoneManager().forgetPlayer(player);
    }
}
