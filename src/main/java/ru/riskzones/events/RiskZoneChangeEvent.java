package ru.riskzones.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.riskzones.zone.ZoneDefinition;

/**
 * Кидается каждый раз, когда игрок меняет зону. Слушай это событие в
 * своём плагине с бафами/донатом, чтобы снимать или возвращать свои
 * эффекты при входе/выходе из конкретной зоны - RiskZones не знает и не
 * должен знать, откуда именно взялся конкретный бафф
 *
 * from/to могут быть null - это значит "вне всех настроенных зон" (аналог
 * старого SAFE). Кастомные зоны определяются в config.yml, у каждой свой
 * {@link ZoneDefinition#getId()}.
 *
 * Событие информационное, не отменяемое: к моменту вызова RiskZones уже
 * применил свою часть логики (полёт, скорость/размер, блокировка команд
 * настраивается отдельно через PlayerCommandPreprocessEvent)
 */
public final class RiskZoneChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ZoneDefinition from;
    private final ZoneDefinition to;

    public RiskZoneChangeEvent(Player player, ZoneDefinition from, ZoneDefinition to) {
        super(); // всегда вызывается синхронно (из PlayerMoveEvent/таймера на главном потоке)
        this.player = player;
        this.from = from;
        this.to = to;
    }

    public Player getPlayer() {
        return player;
    }

    /** null = игрок был вне всех настроенных зон. */
    public ZoneDefinition getFrom() {
        return from;
    }

    /** null = игрок теперь вне всех настроенных зон. */
    public ZoneDefinition getTo() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
