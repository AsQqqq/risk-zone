package ru.riskzones.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.riskzones.RiskZonesPlugin;
import ru.riskzones.zone.ZoneDefinition;

/**
 * /riskzones reload|info|zone <игрок>. Все пользовательские строки - из
 * lang/<language>.yml (command.*), не хардкод, чтобы менялись без
 * пересборки плагина
 */
public final class RiskZonesCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final RiskZonesPlugin plugin;

    public RiskZonesCommand(RiskZonesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPluginConfig();
                sendLang(sender, "command.reload-success", "&aRiskZones: конфиг и переводы перезагружены.");
            }
            case "info" -> handleInfo(sender);
            case "zone" -> handleZone(sender, args);
            default -> {
                sendLang(sender, "command.unknown-subcommand", "&cНеизвестная подкоманда &f%input%&c.",
                        "%input%", args[0]);
                sendUsage(sender);
            }
        }
        return true;
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendLang(sender, "command.player-only", "&cЭту подкоманду нужно выполнять от лица игрока.");
            return;
        }
        ZoneDefinition zone = plugin.getZoneManager().resolve(player.getLocation());
        sendLang(sender, "command.info-world", "&7Мир: &f%world%", "%world%", player.getWorld().getName());
        sendLang(sender, "command.info-zone", "&7Ваша зона: %zone%", "%zone%", zoneLabel(zone));
        sendCreditsFooter(sender);
    }

    private void handleZone(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendLang(sender, "command.zone-usage", "&cИспользование: /riskzones zone <игрок>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sendLang(sender, "command.player-not-found", "&cИгрок не найден или не в сети.");
            return;
        }
        ZoneDefinition zone = plugin.getZoneManager().getLastKnownZone(target);
        sendLang(sender, "command.other-player-zone", "&f%player% &7сейчас в зоне: %zone%",
                "%player%", target.getName(), "%zone%", zoneLabel(zone));
    }

    private void sendUsage(CommandSender sender) {
        sendLang(sender, "command.usage-header", "&6RiskZones - доступные команды:");
        for (String line : plugin.getLangManager().getStringList("command.usage")) {
            sender.sendMessage(LEGACY.deserialize(line));
        }
        sendCreditsFooter(sender);
    }

    private String zoneLabel(ZoneDefinition zone) {
        if (zone != null) return zone.getColoredDisplayName();
        return plugin.getLangManager().getString("command.info-no-zone", "&7вне зон (без ограничений)");
    }

    private void sendCreditsFooter(CommandSender sender) {
        String line = plugin.getLangManager()
                .getString("credits.line", "&8▪ &7Разработано: &f%developer%")
                .replace("%developer%", plugin.getPluginConfig().getDeveloperName());
        sender.sendMessage(LEGACY.deserialize(line));

        Component telegram = Component.text("[Telegram]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(plugin.getPluginConfig().getTelegramUrl()))
                .hoverEvent(HoverEvent.showText(Component.text(plugin.getPluginConfig().getTelegramUrl())));
        Component github = Component.text("[GitHub]", NamedTextColor.GRAY)
                .clickEvent(ClickEvent.openUrl(plugin.getPluginConfig().getGithubUrl()))
                .hoverEvent(HoverEvent.showText(Component.text(plugin.getPluginConfig().getGithubUrl())));

        sender.sendMessage(Component.text("  ").append(telegram).append(Component.text("  ")).append(github));
    }

    private void sendLang(CommandSender sender, String path, String fallback, String... placeholders) {
        String text = plugin.getLangManager().getString(path, fallback);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace(placeholders[i], placeholders[i + 1]);
        }
        sender.sendMessage(LEGACY.deserialize(text));
    }
}
