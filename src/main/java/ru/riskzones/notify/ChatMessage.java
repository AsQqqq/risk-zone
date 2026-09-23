package ru.riskzones.notify;

/** Личное сообщение в чат (видно только адресату) */
public record ChatMessage(boolean enabled, String text) {
}
