package ru.riskzones.zone;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;
import ru.riskzones.notify.ActionBarMessage;
import ru.riskzones.notify.ChatMessage;
import ru.riskzones.notify.SoundMessage;
import ru.riskzones.notify.TitleMessage;
import ru.riskzones.notify.ZoneNotification;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Разбирает одну секцию worlds.<мир>.zones.<id> в {@link ZoneDefinition}.
 * В отличие от старой схемы, текст уведомлений читается прямо из этой же
 * секции конфига (chat.text/title.title/...), а не из lang - lang теперь
 * только для системных сообщений команды /riskzones
 *
 * {@link #loadNotification} также переиспользуется для глобального
 * no-zone-messages в PluginConfig - там та же форма (chat/action-bar/
 * title/sound), просто не привязана к конкретной зоне
 */
public final class ZoneDefinitionLoader {

    private static final Sound FALLBACK_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;

    private ZoneDefinitionLoader() {
    }

    public static ZoneDefinition load(String id, ConfigurationSection section, Logger logger) {
        double from = section.getDouble("from", 0);
        double to = section.getDouble("to", -1);
        String displayName = section.getString("display-name", id);
        String color = section.getString("color", "&f");

        boolean disableFlight = section.getBoolean("disable-flight", false);

        Set<PotionEffectType> effects = new HashSet<>();
        for (String name : section.getStringList("strip-potion-effects")) {
            PotionEffectType type = PotionEffectType.getByName(name.trim().toUpperCase());
            if (type != null) {
                effects.add(type);
            }
        }

        Set<String> blockedCommands = new HashSet<>();
        for (String cmd : section.getStringList("blocked-commands")) {
            blockedCommands.add(cmd.trim().toLowerCase());
        }
        String blockedCommandMessage = section.getString("blocked-command-message",
                "&cКоманда &f/%command% &cнедоступна в этой зоне.");

        ZoneDefinition.ResetAttributes resetAttributes =
                loadResetAttributes(section.getConfigurationSection("reset-attributes"));

        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        ConfigurationSection enterSection = messagesSection == null ? null
                : messagesSection.getConfigurationSection("enter");
        ZoneNotification enterMessages = loadNotification(enterSection, "zones." + id + ".messages.enter", logger);

        ZoneDefinition.ApproachWarning approachWarning =
                loadApproachWarning(section.getConfigurationSection("approach-warning"), id, logger);

        return new ZoneDefinition(id, from, to, displayName, color, disableFlight, effects,
                blockedCommands, blockedCommandMessage, resetAttributes, enterMessages, approachWarning);
    }

    private static ZoneDefinition.ResetAttributes loadResetAttributes(ConfigurationSection s) {
        boolean enabled = s != null && s.getBoolean("enabled", true);

        ConfigurationSection walk = s == null ? null : s.getConfigurationSection("walk-speed");
        boolean walkEnabled = walk == null || walk.getBoolean("enabled", true);
        float walkValue = (float) (walk == null ? 0.1 : walk.getDouble("value", 0.1));
        float walkFov = (float) (walk == null ? 0.2 : walk.getDouble("fov", 0.2));

        ConfigurationSection fly = s == null ? null : s.getConfigurationSection("fly-speed");
        boolean flyEnabled = fly == null || fly.getBoolean("enabled", true);
        float flyValue = (float) (fly == null ? 0.1 : fly.getDouble("value", 0.1));

        ConfigurationSection scale = s == null ? null : s.getConfigurationSection("scale");
        boolean scaleEnabled = scale == null || scale.getBoolean("enabled", true);
        double scaleValue = scale == null ? 1.0 : scale.getDouble("value", 1.0);

        return new ZoneDefinition.ResetAttributes(enabled,
                walkEnabled, walkValue, walkFov, flyEnabled, flyValue, scaleEnabled, scaleValue);
    }

    private static ZoneDefinition.ApproachWarning loadApproachWarning(ConfigurationSection s, String zoneId, Logger logger) {
        boolean enabled = s != null && s.getBoolean("enabled", false);
        double distance = s == null ? 500 : s.getDouble("distance", 500);
        long repeatIntervalSeconds = s == null ? 30 : s.getLong("repeat-interval-seconds", 30);
        ZoneNotification notification = loadNotification(s, "zones." + zoneId + ".approach-warning", logger);
        return new ZoneDefinition.ApproachWarning(enabled, distance, repeatIntervalSeconds, notification);
    }

    public static ZoneNotification loadNotification(ConfigurationSection section, String context, Logger logger) {
        return new ZoneNotification(
                loadChat(section == null ? null : section.getConfigurationSection("chat")),
                loadActionBar(section == null ? null : section.getConfigurationSection("action-bar")),
                loadTitle(section == null ? null : section.getConfigurationSection("title")),
                loadSound(section == null ? null : section.getConfigurationSection("sound"), context, logger));
    }

    private static ChatMessage loadChat(ConfigurationSection s) {
        boolean enabled = s == null || s.getBoolean("enabled", true);
        String text = s == null ? "" : s.getString("text", "");
        return new ChatMessage(enabled, text);
    }

    private static ActionBarMessage loadActionBar(ConfigurationSection s) {
        boolean enabled = s == null || s.getBoolean("enabled", true);
        String text = s == null ? "" : s.getString("text", "");
        return new ActionBarMessage(enabled, text);
    }

    private static TitleMessage loadTitle(ConfigurationSection s) {
        boolean enabled = s == null || s.getBoolean("enabled", true);
        String title = s == null ? "" : s.getString("title", "");
        String subtitle = s == null ? "" : s.getString("subtitle", "");
        int fadeIn = s == null ? 10 : s.getInt("fade-in", 10);
        int stay = s == null ? 60 : s.getInt("stay", 60);
        int fadeOut = s == null ? 10 : s.getInt("fade-out", 10);
        return new TitleMessage(enabled, title, subtitle, fadeIn, stay, fadeOut);
    }

    private static SoundMessage loadSound(ConfigurationSection s, String context, Logger logger) {
        boolean enabled = s == null || s.getBoolean("enabled", true);
        float volume = s == null ? 1.0f : (float) s.getDouble("volume", 1.0);
        float pitch = s == null ? 1.0f : (float) s.getDouble("pitch", 1.0);

        Sound sound = FALLBACK_SOUND;
        String name = s == null ? null : s.getString("name");
        if (name != null && !name.isBlank()) {
            Sound resolved = resolveSound(name);
            if (resolved != null) {
                sound = resolved;
            } else {
                logger.warning("RiskZones: неизвестный звук '" + name + "' в " + context
                        + ".sound.name - использую звук по умолчанию.");
            }
        }
        return new SoundMessage(enabled, sound, volume, pitch);
    }

    /** Принимает и ключ реестра ("entity.player.levelup"), и имя константы Bukkit ("ENTITY_PLAYER_LEVELUP") */
    private static Sound resolveSound(String rawName) {
        String trimmed = rawName.trim();

        Sound byKey = Registry.SOUNDS.get(NamespacedKey.minecraft(trimmed.toLowerCase()));
        if (byKey != null) {
            return byKey;
        }

        try {
            Object value = Sound.class.getField(trimmed.toUpperCase()).get(null);
            if (value instanceof Sound sound) {
                return sound;
            }
        } catch (ReflectiveOperationException ignored) {
            // не константа Bukkit - оставляем null, вызывающий код залогирует и подставит фолбэк
        }

        return null;
    }
}
