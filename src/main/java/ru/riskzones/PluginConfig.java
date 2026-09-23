package ru.riskzones;

import org.bukkit.configuration.file.FileConfiguration;
import ru.riskzones.notify.ZoneNotification;
import ru.riskzones.zone.ZoneDefinitionLoader;

import java.util.logging.Logger;

/**
 * Глобальные настройки, не привязанные к конкретной зоне (ограничения и
 * тексты каждой зоны теперь самостоятельны - см. {@link ru.riskzones.zone.ZoneDefinition},
 * их читает {@link ru.riskzones.zone.ZoneManager}).
 */
public final class PluginConfig {

    private String developerName = "Danya";
    private String telegramUrl = "";
    private String githubUrl = "";

    private long safetyCheckIntervalTicks = 100;

    private ZoneNotification noZoneMessages;

    public void loadFrom(FileConfiguration config, Logger logger) {
        developerName = config.getString("credits.developer", developerName);
        telegramUrl = config.getString("credits.telegram-url", telegramUrl);
        githubUrl = config.getString("credits.github-url", githubUrl);

        safetyCheckIntervalTicks = config.getLong("safety-check-interval-ticks", 100);

        noZoneMessages = ZoneDefinitionLoader.loadNotification(
                config.getConfigurationSection("no-zone-messages"), "no-zone-messages", logger);
    }

    public String getDeveloperName() {
        return developerName;
    }

    public String getTelegramUrl() {
        return telegramUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public long getSafetyCheckIntervalTicks() {
        return safetyCheckIntervalTicks;
    }

    public ZoneNotification getNoZoneMessages() {
        return noZoneMessages;
    }
}
