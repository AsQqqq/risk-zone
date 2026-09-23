package ru.riskzones.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.riskzones.RiskZonesPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Управляет папкой lang/, которая создаётся рядом с config.yml. На старте
 * выкладывает встроенные ru.yml/en.yml, если их там ещё нет - уже
 * существующие файлы никогда не трогаются и не перезаписываются, так что
 * админ может свободно править тексты. Активный язык выбирается ключом
 * "language" в config.yml и подхватывается заново при каждом /riskzones reload
 */
public final class LangManager {

    private static final List<String> BUNDLED_LANGUAGES = List.of("ru", "en");
    private static final String FALLBACK_LANGUAGE = "ru";

    private final RiskZonesPlugin plugin;
    private YamlConfiguration active;
    private String activeCode = FALLBACK_LANGUAGE;

    public LangManager(RiskZonesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Кладёт в lang/ встроенные переводы, если их там ещё нет. Вызывается один раз в onEnable */
    public void saveDefaults() {
        File folder = langFolder();
        for (String code : BUNDLED_LANGUAGES) {
            File target = new File(folder, code + ".yml");
            if (!target.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
    }

    /** Загружает файл перевода по коду языка из config.yml. Вызывать при (пере)загрузке конфига */
    public void reload(String requestedCode) {
        String code = normalize(requestedCode);
        File file = new File(langFolder(), code + ".yml");

        YamlConfiguration loaded;
        if (file.exists()) {
            loaded = YamlConfiguration.loadConfiguration(file);
        } else {
            plugin.getLogger().warning("RiskZones: файл языка '" + code
                    + ".yml' не найден в lang/, использую встроенный " + FALLBACK_LANGUAGE + ".");
            loaded = loadBundledResource(FALLBACK_LANGUAGE);
            code = FALLBACK_LANGUAGE;
        }

        // Подстраховка: если админ отредактировал файл и что-то удалил или
        // опечатался в ключе, недостающие строки подхватятся из встроенной
        // версии того же языка (а если это не ru/en - из встроенного ru)
        loaded.setDefaults(loadBundledFallback(code));

        active = loaded;
        activeCode = code;
    }

    public String getString(String path, String fallback) {
        if (active == null) return fallback;
        String value = active.getString(path);
        return value != null ? value : fallback;
    }

    public List<String> getStringList(String path) {
        if (active == null) return List.of();
        return active.getStringList(path);
    }

    public String getActiveCode() {
        return activeCode;
    }

    private YamlConfiguration loadBundledFallback(String code) {
        YamlConfiguration bundled = loadBundledResource(code);
        if (bundled != null) return bundled;
        YamlConfiguration ru = loadBundledResource(FALLBACK_LANGUAGE);
        return ru != null ? ru : new YamlConfiguration();
    }

    private YamlConfiguration loadBundledResource(String code) {
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private File langFolder() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    private static String normalize(String raw) {
        return raw == null || raw.isBlank() ? FALLBACK_LANGUAGE : raw.trim().toLowerCase();
    }
}
