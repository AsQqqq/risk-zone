package ru.riskzones;

import org.bukkit.plugin.java.JavaPlugin;
import ru.riskzones.command.RiskZonesCommand;
import ru.riskzones.lang.LangManager;
import ru.riskzones.listener.CommandBlockListener;
import ru.riskzones.listener.MovementListener;
import ru.riskzones.zone.ZoneManager;
import ru.riskzones.zone.ZoneUpdateService;

public final class RiskZonesPlugin extends JavaPlugin {

    private static RiskZonesPlugin instance;

    private final ZoneManager zoneManager = new ZoneManager();
    private final PluginConfig pluginConfig = new PluginConfig();
    private ZoneUpdateService zoneUpdateService;
    private LangManager langManager;

    @Override
    public void onEnable() {
        instance = this;
        zoneUpdateService = new ZoneUpdateService(this);
        langManager = new LangManager(this);

        saveDefaultConfig();
        langManager.saveDefaults();
        reloadPluginConfig();

        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);

        var cmd = getCommand("riskzones");
        if (cmd != null) {
            cmd.setExecutor(new RiskZonesCommand(this));
        }

        // Подстраховочный таймер: пересчитывает зону всем онлайн-игрокам
        // напрямую через ZoneUpdateService (без фейковых Bukkit-событий) -
        // на случай, если сторонний плагин переместил игрока в обход
        // обычных move/teleport событий
        long interval = pluginConfig.getSafetyCheckIntervalTicks();
        if (interval > 0) {
            getServer().getScheduler().runTaskTimer(this, () ->
                    getServer().getOnlinePlayers().forEach(zoneUpdateService::update),
                    interval, interval);
        }

        int worldsConfigured = getConfig().getConfigurationSection("worlds") == null
                ? 0
                : getConfig().getConfigurationSection("worlds").getKeys(false).size();
        getLogger().info("RiskZones включён. Миров с настроенными зонами: " + worldsConfigured);
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        langManager.reload(getConfig().getString("language", "ru"));
        zoneManager.loadFrom(getConfig().getConfigurationSection("worlds"), getLogger());
        pluginConfig.loadFrom(getConfig(), getLogger());
    }

    public static RiskZonesPlugin getInstance() {
        return instance;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public ZoneUpdateService getZoneUpdateService() {
        return zoneUpdateService;
    }

    public LangManager getLangManager() {
        return langManager;
    }
}
