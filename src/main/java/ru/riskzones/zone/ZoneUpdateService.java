package ru.riskzones.zone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import ru.riskzones.RiskZonesPlugin;
import ru.riskzones.events.RiskZoneChangeEvent;
import ru.riskzones.notify.ActionBarMessage;
import ru.riskzones.notify.ChatMessage;
import ru.riskzones.notify.SoundMessage;
import ru.riskzones.notify.TitleMessage;
import ru.riskzones.notify.ZoneNotification;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Общая логика пересчёта зоны и применения её эффектов (полёт, сброс скорости/размера, 
 * уведомления, предупреждение о
 * приближении, событие для сторонних плагинов). Вызывается и из реальных
 * Bukkit-событий (движение/телепорт/смена мира), и из подстраховочного
 * таймера - без имитации фейковых событий Bukkit, которые могли бы сбить
 * с толку античит и другие плагины. Все ограничения/тексты берутся из
 * {@link ZoneDefinition} переданной зоны - сам сервис зоно-агностичен
 */
public final class ZoneUpdateService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Именно в этом диапазоне Bukkit принимает setWalkSpeed/setFlySpeed -
    // значение за пределами кинет IllegalArgumentException
    private static final float SPEED_FIELD_MIN = -1f;
    private static final float SPEED_FIELD_MAX = 1f;

    // Ванильные границы атрибута generic.movement_speed
    private static final double MOVEMENT_SPEED_ATTRIBUTE_MIN = 0.0;
    private static final double MOVEMENT_SPEED_ATTRIBUTE_MAX = 1024.0;

    // Ванильные границы атрибута generic.scale - за них его не пускают
    private static final double SCALE_MIN = 0.0625;
    private static final double SCALE_MAX = 16.0;

    private final RiskZonesPlugin plugin;

    public ZoneUpdateService(RiskZonesPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player player) {
        ZoneDefinition newZone = plugin.getZoneManager().resolve(player.getLocation());
        ZoneDefinition oldZone = plugin.getZoneManager().getLastKnownZone(player);

        // Не привязано к смене зоны - предупреждение должно сработать, пока
        // игрок ещё не в следующей зоне, просто приближается к её границе
        checkApproachWarning(player);

        if (Objects.equals(idOf(newZone), idOf(oldZone))) {
            // Зона не сменилась, но правила зоны (полёт/скорость/размер)
            // могли переналожить кто-то другой (другой плагин, зелье,
            // /gamemode) уже после нашего применения при входе -
            // перепроверяем и гасим их снова, пока игрок в этой зоне
            reinforceZoneRules(player, newZone);
            return;
        }

        plugin.getZoneManager().setLastKnownZone(player, newZone);

        applyFlightRule(player, oldZone, newZone);
        if (newZone != null) {
            stripConfiguredEffects(player, newZone);
            resetAttributesToStock(player, newZone);
        }
        notifyZoneChange(player, newZone);

        plugin.getServer().getPluginManager().callEvent(
                new RiskZoneChangeEvent(player, oldZone, newZone));
    }

    private static String idOf(ZoneDefinition zone) {
        return zone == null ? null : zone.getId();
    }

    public void restoreFlightIfCached(Player player) {
        Boolean cached = plugin.getZoneManager().popCachedAllowFlight(player);
        if (cached != null && player.isOnline()) {
            player.setAllowFlight(cached);
        }
    }

    private void applyFlightRule(Player player, ZoneDefinition oldZone, ZoneDefinition newZone) {
        if (player.hasPermission("riskzones.bypass.flight")) return;

        boolean newDisables = newZone != null && newZone.isDisableFlight();
        boolean oldDisabled = oldZone != null && oldZone.isDisableFlight();

        if (newDisables) {
            forceFlightOff(player);
        } else if (oldDisabled) {
            restoreFlightIfCached(player);
        }
    }

    /**
     * Вызывается, даже когда зона игрока не поменялась. Нужен, чтобы
     * поймать переналоженный полёт/скорость/размер уже после того, как
     * плагин их сбросил при входе - иначе они могли остаться работать до
     * следующей смены зоны
     */
    private void reinforceZoneRules(Player player, ZoneDefinition zone) {
        if (zone == null) return;
        if (zone.isDisableFlight() && !player.hasPermission("riskzones.bypass.flight")) {
            forceFlightOff(player);
        }
        resetAttributesToStock(player, zone);
    }

    private void forceFlightOff(Player player) {
        plugin.getZoneManager().cacheAllowFlight(player);
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
    }

    private void stripConfiguredEffects(Player player, ZoneDefinition zone) {
        zone.getStripPotionEffects().forEach(effectType -> {
            if (player.hasPotionEffect(effectType)) {
                player.removePotionEffect(effectType);
            }
        });
    }

    /**
     * Сбрасывает то, что могли поменять сторонние плагины: скорость
     * ходьбы/полёта и размер персонажа (донат-баффы, /speed, ростомеры
     * и т.п.) - до значений, заданных в зоне. Вызывается и при входе в
     * зону, и постоянно, пока игрок в ней остаётся (см. update())
     */
    private void resetAttributesToStock(Player player, ZoneDefinition zone) {
        ZoneDefinition.ResetAttributes settings = zone.getResetAttributes();
        if (!settings.enabled()) return;
        if (player.hasPermission("riskzones.bypass.attributes")) return;

        if (settings.walkSpeedEnabled()) {
            // Реальную скорость ходьбы по земле определяет атрибут
            // movement_speed. setWalkSpeed на современном клиенте её уже
            // не двигает - это отдельный, чисто косметический параметр,
            // влияющий только на FOV-эффект при ходьбе
            resetAttributeToValue(player, Attribute.MOVEMENT_SPEED,
                    clampMovementSpeedAttribute(settings.walkSpeedValue()));
            player.setWalkSpeed(clampSpeed(settings.walkSpeedFov()));
        }
        if (settings.flySpeedEnabled()) {
            player.setFlySpeed(clampSpeed(settings.flySpeedValue()));
            resetAttributeToDefault(player, Attribute.FLYING_SPEED);
        }
        if (settings.scaleEnabled()) {
            resetAttributeToValue(player, Attribute.SCALE, clampScale(settings.scaleValue()));
        }
    }

    private static float clampSpeed(float value) {
        return Math.max(SPEED_FIELD_MIN, Math.min(SPEED_FIELD_MAX, value));
    }

    private static double clampMovementSpeedAttribute(double value) {
        return Math.max(MOVEMENT_SPEED_ATTRIBUTE_MIN, Math.min(MOVEMENT_SPEED_ATTRIBUTE_MAX, value));
    }

    private static double clampScale(double value) {
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, value));
    }

    /** Снимает модификаторы плагинов и возвращает базовое значение атрибута к ванильному */
    private void resetAttributeToDefault(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        resetAttributeToValue(player, attribute, instance.getDefaultValue());
    }

    /**
     * Снимает модификаторы, добавленные плагинами, и ставит заданное
     * базовое значение. Ванильные модификаторы (namespace "minecraft") не
     * трогаем - например, у movement_speed это буст от спринта, который
     * игра сама постоянно поддерживает; если его снимать, ваниль тут же
     * накладывает его заново, и это постоянное выдёргивание сбивает
     * клиентскую анимацию FOV
     */
    private void resetAttributeToValue(Player player, Attribute attribute, double baseValue) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (!NamespacedKey.MINECRAFT.equals(modifier.getKey().getNamespace())) {
                instance.removeModifier(modifier);
            }
        }
        instance.setBaseValue(baseValue);
    }

    /**
     * Предупреждает игрока, пока он ещё не в следующей по радиусу зоне, но
     * приближается к её from-границе (по расстоянию из настроек этой
     * зоны). Срабатывает один раз при входе в полосу предупреждения,
     * дальше - не чаще repeat-interval, пока игрок из полосы не выйдет
     * (вглубь текущей зоны/за пределы) или не войдёт в саму целевую зону
     */
    private void checkApproachWarning(Player player) {
        ZoneDefinition next = plugin.getZoneManager().nextZone(player.getLocation());
        if (next == null) {
            plugin.getZoneManager().setApproachingZoneId(player, null);
            return;
        }

        ZoneDefinition.ApproachWarning warning = next.getApproachWarning();
        if (!warning.enabled()) {
            plugin.getZoneManager().setApproachingZoneId(player, null);
            return;
        }
        if (!player.hasPermission("riskzones.notify")) return;

        double distance = plugin.getZoneManager().distanceToZoneEdge(player.getLocation(), next);
        if (distance < 0 || distance > warning.distance()) {
            plugin.getZoneManager().setApproachingZoneId(player, null);
            return;
        }

        boolean alreadyInBand = next.getId().equals(plugin.getZoneManager().getApproachingZoneId(player));
        long repeatIntervalMillis = warning.repeatIntervalSeconds() * 1000L;
        long sinceLastWarning = System.currentTimeMillis()
                - plugin.getZoneManager().getLastZoneWarningAtMillis(player);

        boolean shouldWarn = !alreadyInBand || (repeatIntervalMillis > 0 && sinceLastWarning >= repeatIntervalMillis);
        if (!shouldWarn) return;

        plugin.getZoneManager().setApproachingZoneId(player, next.getId());
        plugin.getZoneManager().markZoneWarnedNow(player);

        sendApproachWarning(player, warning.notification(), (int) Math.ceil(distance));
    }

    private void sendApproachWarning(Player player, ZoneNotification notification, int distanceBlocks) {
        String distanceText = String.valueOf(distanceBlocks);

        sendChat(player, withDistance(notification.chat(), distanceText));
        sendActionBar(player, withDistance(notification.actionBar(), distanceText));
        sendTitle(player, withDistance(notification.title(), distanceText));
        playSound(player, notification.sound());
    }

    private static ChatMessage withDistance(ChatMessage message, String distance) {
        return new ChatMessage(message.enabled(), replaceDistance(message.text(), distance));
    }

    private static ActionBarMessage withDistance(ActionBarMessage message, String distance) {
        return new ActionBarMessage(message.enabled(), replaceDistance(message.text(), distance));
    }

    private static TitleMessage withDistance(TitleMessage message, String distance) {
        return new TitleMessage(message.enabled(),
                replaceDistance(message.title(), distance),
                replaceDistance(message.subtitle(), distance),
                message.fadeInTicks(), message.stayTicks(), message.fadeOutTicks());
    }

    private static String replaceDistance(String text, String distance) {
        return text == null ? null : text.replace("%distance%", distance);
    }

    private void notifyZoneChange(Player player, ZoneDefinition newZone) {
        if (!player.hasPermission("riskzones.notify")) return;

        ZoneNotification notification = newZone != null
                ? newZone.getEnterMessages()
                : plugin.getPluginConfig().getNoZoneMessages();

        sendChat(player, notification.chat());
        sendActionBar(player, notification.actionBar());
        sendTitle(player, notification.title());
        playSound(player, notification.sound());
    }

    private void sendChat(Player player, ChatMessage message) {
        if (!message.enabled() || message.text() == null || message.text().isBlank()) return;
        player.sendMessage(LEGACY.deserialize(message.text()));
    }

    private void sendActionBar(Player player, ActionBarMessage message) {
        if (!message.enabled() || message.text() == null || message.text().isBlank()) return;
        player.sendActionBar(LEGACY.deserialize(message.text()));
    }

    private void sendTitle(Player player, TitleMessage message) {
        if (!message.enabled()) return;

        boolean hasTitle = message.title() != null && !message.title().isBlank();
        boolean hasSubtitle = message.subtitle() != null && !message.subtitle().isBlank();
        if (!hasTitle && !hasSubtitle) return;

        Component main = hasTitle ? LEGACY.deserialize(message.title()) : Component.empty();
        Component sub = hasSubtitle ? LEGACY.deserialize(message.subtitle()) : Component.empty();
        Title.Times times = Title.Times.times(
                ticksToDuration(message.fadeInTicks()),
                ticksToDuration(message.stayTicks()),
                ticksToDuration(message.fadeOutTicks()));

        player.showTitle(Title.title(main, sub, times));
    }

    private void playSound(Player player, SoundMessage message) {
        if (!message.enabled() || message.sound() == null) return;
        player.playSound(player.getLocation(), message.sound(), message.volume(), message.pitch());
    }

    private static Duration ticksToDuration(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }
}
