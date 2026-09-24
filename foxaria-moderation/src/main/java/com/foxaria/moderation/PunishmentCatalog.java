package com.foxaria.moderation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PunishmentCatalog {

    private final FileConfiguration config;

    public PunishmentCatalog(FileConfiguration config) {
        this.config = config;
    }

    public Optional<Entry> byCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        ConfigurationSection sec = reasonsSection();
        if (sec == null) {
            return Optional.empty();
        }
        for (String key : sec.getKeys(false)) {
            if (key.equalsIgnoreCase(code)) {
                return Optional.of(readEntry(sec.getConfigurationSection(key), key));
            }
        }
        return Optional.empty();
    }

    public List<Entry> all() {
        ConfigurationSection sec = reasonsSection();
        if (sec == null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            entries.add(readEntry(sec.getConfigurationSection(key), key));
        }
        entries.sort(Comparator.comparing(Entry::code, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private ConfigurationSection reasonsSection() {
        return config.getConfigurationSection("punishments.reasons");
    }

    private Entry readEntry(ConfigurationSection sec, String key) {
        if (sec == null) {
            return new Entry(key, "BAN", 0L, key, "Без описания", "Без описания");
        }
        return new Entry(
            key,
            sec.getString("type", "BAN").toUpperCase(Locale.ROOT),
            sec.getLong("duration-seconds", 0L),
            sec.getString("title", key),
            sec.getString("description", "Без описания"),
            sec.getString("rules-text", sec.getString("description", "Без описания"))
        );
    }

    public record Entry(
        String code,
        String type,
        long durationSeconds,
        String title,
        String description,
        String rulesText
    ) {
        public boolean temporary() {
            return durationSeconds > 0L;
        }
    }
}
