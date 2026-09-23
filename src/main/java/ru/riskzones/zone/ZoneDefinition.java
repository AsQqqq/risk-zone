package ru.riskzones.zone;

import org.bukkit.potion.PotionEffectType;
import ru.riskzones.notify.ZoneNotification;

import java.util.Set;

/**
 * Полностью самостоятельное описание одной кастомной зоны: диапазон
 * дистанций от центра мира (from/to, to = -1 значит "бесконечно" - так
 * может быть только у самой дальней зоны, см. валидацию в
 * {@link WorldZoneConfig#loadFrom}), отображение и весь набор ограничений
 * и текстов. Раньше это было жёстко зашито как enum Zone {GREEN, BLACK} с
 * общими на весь плагин настройками - теперь каждая зона независима
 */
public final class ZoneDefinition {

    public record ResetAttributes(
            boolean enabled,
            boolean walkSpeedEnabled, float walkSpeedValue, float walkSpeedFov,
            boolean flySpeedEnabled, float flySpeedValue,
            boolean scaleEnabled, double scaleValue) {
    }

    public record ApproachWarning(
            boolean enabled, double distance, long repeatIntervalSeconds, ZoneNotification notification) {
    }

    private final String id;
    private final double from;
    private final double to;
    private final String displayName;
    private final String color;

    private final boolean disableFlight;
    private final Set<PotionEffectType> stripPotionEffects;
    private final Set<String> blockedCommands;
    private final String blockedCommandMessage;
    private final ResetAttributes resetAttributes;
    private final ZoneNotification enterMessages;
    private final ApproachWarning approachWarning;

    public ZoneDefinition(String id, double from, double to, String displayName, String color,
                           boolean disableFlight, Set<PotionEffectType> stripPotionEffects,
                           Set<String> blockedCommands, String blockedCommandMessage,
                           ResetAttributes resetAttributes, ZoneNotification enterMessages,
                           ApproachWarning approachWarning) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.displayName = displayName;
        this.color = color;
        this.disableFlight = disableFlight;
        this.stripPotionEffects = stripPotionEffects;
        this.blockedCommands = blockedCommands;
        this.blockedCommandMessage = blockedCommandMessage;
        this.resetAttributes = resetAttributes;
        this.enterMessages = enterMessages;
        this.approachWarning = approachWarning;
    }

    /** true, если дистанция от центра мира попадает в [from, to) этой зоны (to < 0 - без верхней границы) */
    public boolean containsDistance(double distance) {
        if (distance < from) return false;
        return to < 0 || distance < to;
    }

    public String getId() {
        return id;
    }

    public double getFrom() {
        return from;
    }

    public double getTo() {
        return to;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    /** Готовая для вставки в сообщение цветная строка - color + displayName */
    public String getColoredDisplayName() {
        return (color == null ? "" : color) + displayName;
    }

    public boolean isDisableFlight() {
        return disableFlight;
    }

    public Set<PotionEffectType> getStripPotionEffects() {
        return stripPotionEffects;
    }

    public Set<String> getBlockedCommands() {
        return blockedCommands;
    }

    public String getBlockedCommandMessage() {
        return blockedCommandMessage;
    }

    public ResetAttributes getResetAttributes() {
        return resetAttributes;
    }

    public ZoneNotification getEnterMessages() {
        return enterMessages;
    }

    public ApproachWarning getApproachWarning() {
        return approachWarning;
    }
}
