package ru.riskzones.notify;

/**
 * Крупный текст по центру экрана (title) + строка помельче под ним
 * (subtitle). Тайминги - в тиках, как в ванильной команде /title
 */
public record TitleMessage(boolean enabled, String title, String subtitle,
                            int fadeInTicks, int stayTicks, int fadeOutTicks) {
}
