package ru.riskzones.notify;

import org.bukkit.Sound;

/** Звук, проигрываемый только адресату (через Player#playSound) */
public record SoundMessage(boolean enabled, Sound sound, float volume, float pitch) {
}
