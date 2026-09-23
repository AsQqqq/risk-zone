package ru.riskzones.zone;

public enum ZoneShape {
    CIRCLE,
    SQUARE;

    public static ZoneShape parse(String raw, ZoneShape fallback) {
        if (raw == null) return fallback;
        try {
            return ZoneShape.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
