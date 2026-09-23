package ru.riskzones.notify;

/** Полный набор каналов уведомления для одного перехода между зонами */
public record ZoneNotification(ChatMessage chat, ActionBarMessage actionBar,
                                TitleMessage title, SoundMessage sound) {
}
